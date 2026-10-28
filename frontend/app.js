const MAX_POINTS = 50;

let timeData = [];
let cpuData = [];
let ramData = [];
let tempData = [];
let lowerBoundData = [];
let upperBoundData = [];
let anomalyTimes = [];
let anomalyValues = [];

let isPlotInitialized = false;

const layout = {
    paper_bgcolor: 'transparent',
    plot_bgcolor: 'transparent',
    font: { color: '#9ca3af' },
    margin: { t: 40, r: 20, l: 40, b: 40 },
    xaxis: { gridcolor: '#374151', showline: false, zeroline: false },
    yaxis: { gridcolor: '#374151', showline: false, rangemode: 'tozero' },
    showlegend: true,
    legend: { orientation: 'h', y: 1.1, x: 0 },
    hovermode: 'x unified'
};

function updateDashboard(data) {
    if (!data || data.cpu_percent === undefined) return;

    let cpuVal = parseFloat(data.cpu_percent);
    document.getElementById('cpu-value').innerText = cpuVal.toFixed(1) + "%";
    
    let ramVal = null;
    if (data.ram_percent !== undefined) {
        ramVal = parseFloat(data.ram_percent);
        document.getElementById('ram-value').innerText = ramVal.toFixed(1) + "%";
    }
    
    let tempVal = null;
    if (data.gpu_temp_c !== undefined) {
        tempVal = parseFloat(data.gpu_temp_c);
        document.getElementById('gpu-value').innerText = tempVal.toFixed(1) + " °C";
    }

    const now = new Date();

    let aiopsObj = data.aiops;
    if (typeof aiopsObj === 'string') {
        try { aiopsObj = JSON.parse(aiopsObj); } catch(e) {}
    }

    let lower = cpuVal;
    let upper = cpuVal;
    let isAnomaly = false;

    if (aiopsObj) {
        lower = parseFloat(aiopsObj.cpu_lower_bound);
        upper = parseFloat(aiopsObj.cpu_upper_bound);
        isAnomaly = (aiopsObj.is_anomaly === true || String(aiopsObj.is_anomaly).toLowerCase() === 'true');
    }

    timeData.push(now);
    cpuData.push(cpuVal);
    ramData.push(ramVal);
    tempData.push(tempVal);
    lowerBoundData.push(lower);
    upperBoundData.push(upper);

    if (isAnomaly) {
        anomalyTimes.push(now);
        anomalyValues.push(cpuVal);
    } else {
        anomalyTimes.push(now);
        anomalyValues.push(null);
    }

    if (timeData.length > MAX_POINTS) {
        timeData.shift();
        cpuData.shift();
        ramData.shift();
        tempData.shift();
        lowerBoundData.shift();
        upperBoundData.shift();
        anomalyTimes.shift();
        anomalyValues.shift();
    }

    const traceLower = { x: [...timeData], y: [...lowerBoundData], type: 'scatter', mode: 'lines', line: { width: 0 }, showlegend: false, hoverinfo: 'skip' };
    const traceUpper = { x: [...timeData], y: [...upperBoundData], type: 'scatter', mode: 'lines', fill: 'tonexty', fillcolor: 'rgba(52, 211, 153, 0.15)', line: { width: 0 }, name: 'Banda CPU', hoverinfo: 'skip' };
    
    const traceCpu = { x: [...timeData], y: [...cpuData], type: 'scatter', mode: 'lines+markers', line: { color: '#34d399', width: 2 }, marker: { size: 4, color: '#34d399' }, name: 'CPU %' };
    const traceRam = { x: [...timeData], y: [...ramData], type: 'scatter', mode: 'lines', line: { color: '#60a5fa', width: 2 }, name: 'RAM %' };
    const traceTemp = { x: [...timeData], y: [...tempData], type: 'scatter', mode: 'lines', line: { color: '#f59e0b', width: 2 }, name: 'GPU °C' };
    
    const traceAnomalies = { x: [...anomalyTimes], y: [...anomalyValues], type: 'scatter', mode: 'markers', marker: { color: '#ef4444', size: 10, line: { color: '#7f1d1d', width: 2 } }, name: 'Anomalía CPU' };

    const plotData = [traceLower, traceUpper, traceRam, traceTemp, traceCpu, traceAnomalies];

    if (!isPlotInitialized) {
        Plotly.newPlot('metrics-chart', plotData, layout);
        isPlotInitialized = true;
    } else {
        Plotly.react('metrics-chart', plotData, layout);
    }
}

setInterval(() => {
    fetch('http://localhost:8080/api/metrics/latest')
        .then(res => res.json())
        .then(updateDashboard)
        .catch(err => console.error(err));
}, 1000);