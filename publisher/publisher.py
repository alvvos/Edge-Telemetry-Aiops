import json
import time
import subprocess
import psutil
from confluent_kafka import Producer

def get_gpu_temp():
    try:
        raw = subprocess.check_output(
            ['nvidia-smi', '--query-gpu=temperature.gpu', '--format=csv,noheader']
        )
        return int(raw.decode('utf-8').strip())
    except Exception:
        return -1

def collect_metrics():
    return {
        "cpu_percent": psutil.cpu_percent(interval=None),
        "ram_percent": psutil.virtual_memory().percent,
        "gpu_temp_c": get_gpu_temp(),
        "timestamp": int(time.time())
    }

def main():
    producer = Producer({'bootstrap.servers': 'localhost:9092'})
    topic = 'raw_metrics'

    while True:
        metrics = collect_metrics()
        payload = json.dumps(metrics)
        
        producer.produce(topic, value=payload.encode('utf-8'))
        producer.flush()
    
        time.sleep(2)

if __name__ == '__main__':
    psutil.cpu_percent() 
    main()