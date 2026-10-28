import subprocess
import sys
import time

print("Iniciando nodos de telemetría")

publisher = subprocess.Popen([sys.executable, "publisher.py"])
engine = subprocess.Popen([sys.executable, "aiops_engine.py"])

try:
    while True:
        time.sleep(1)
except KeyboardInterrupt:
    print("\nApagando sistema")
    publisher.terminate()
    engine.terminate()
    publisher.wait()
    engine.wait()