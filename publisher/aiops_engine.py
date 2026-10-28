import json
import numpy as np
from confluent_kafka import Consumer, Producer

ALPHA = 0.05
WINDOW_SIZE = 150
EMA_SMOOTHING = 0.3

calibration_scores = []
current_ema = None

consumer = Consumer({
    'bootstrap.servers': 'localhost:9092',
    'group.id': 'aiops-engine-group',
    'auto.offset.reset': 'latest'
})
producer = Producer({'bootstrap.servers': 'localhost:9092'})
consumer.subscribe(['raw_metrics'])

def compute_bounds(prediction, scores, alpha):
    if len(scores) < 20:
        return 0.0, 100.0
    
    n = len(scores)
    quantile_idx = min(int(np.ceil((n + 1) * (1 - alpha))), n) - 1
    q = np.sort(scores)[quantile_idx]
    
    return max(0.0, prediction - q), min(100.0, prediction + q)

try:
    while True:
        msg = consumer.poll(0.1)
        if msg is None or msg.error():
            continue

        data = json.loads(msg.value().decode('utf-8'))
        y_t = data['cpu_percent']

        if current_ema is None:
            current_ema = y_t
        hat_y_t = current_ema

        lower_bound, upper_bound = compute_bounds(hat_y_t, calibration_scores, ALPHA)
        
        is_anomaly = (y_t < lower_bound) or (y_t > upper_bound)

        s_t = abs(y_t - hat_y_t)
        calibration_scores.append(s_t)
        if len(calibration_scores) > WINDOW_SIZE:
            calibration_scores.pop(0)
            
        current_ema = (EMA_SMOOTHING * y_t) + ((1 - EMA_SMOOTHING) * current_ema)

        data['aiops'] = {
            'cpu_predicted': round(float(hat_y_t), 2),
            'cpu_lower_bound': round(float(lower_bound), 2),
            'cpu_upper_bound': round(float(upper_bound), 2),
            'is_anomaly': bool(is_anomaly)
        }
        producer.produce('aiops_metrics', json.dumps(data).encode('utf-8'))
        producer.poll(0)

except KeyboardInterrupt:
    pass
finally:
    consumer.close()
    producer.flush()