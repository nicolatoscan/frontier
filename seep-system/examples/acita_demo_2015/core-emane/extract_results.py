#!/usr/bin/python
import re, pandas as pd

def is_src_log(f):
    f_type = log_type(f) 
    return f_type in ['SOURCE', 'VIDEO_SOURCE', 'LOCATION_SOURCE']

def is_sink_log(f):
    return log_type(f) == 'SINK'

def is_finished_sink_log(f):
    regex = re.compile(r'SNK: FINISHED with total tuples=(\d+),total bytes=(\d+),t=(\d+)')
    for line in f:
        match = re.search(regex, line)
        if match: return True 
    return False

def is_processor_log(f):
    #return log_type(f) == 'PROCESSOR'
    f_type = log_type(f) 
    return f_type in ['PROCESSOR', 'FACE_DETECTOR', 'FACE_RECOGNIZER', 'JOIN', 'HEATMAP_JOIN']

def log_type(f):
    regex = re.compile(r'Setting up (.*) operator with id=(.*)$')
    for line in f:
        match = re.search(regex, line)
        if match: return match.group(1)

    raise Exception("Unknown log type!")

def src_tx_begin(f):
    """
    return t_begin
    """
    regex = re.compile(r'Source sending started at t=(\d+)')
    for line in f:
        match = re.search(regex, line)
	if match: return int(match.group(1))

    return None

def sink_rx_begin(f):
    """
    returns t_begin
    """
    regex = re.compile(r'SNK: Received initial tuple at t=(\d+)')
    for line in f:
        match = re.search(regex, line)
	if match: return int(match.group(1))

    return None

def sink_rx_end(f):
    """
    returns (total tuples, total bytes, t_end)
    """
    regex = re.compile(r'SNK: FINISHED with total tuples=(\d+),total bytes=(\d+),t=(\d+)')
    for line in f:
        match = re.search(regex, line)
        if match:
            return (int(match.group(1)), int(match.group(2)), int(match.group(3)))

    return None
   
def sink_rx_latencies(f):
    """
    returns a list of (tsrx, latency)
    """
    tuple_records = sink_rx_tuples(f)
    latencies = map(lambda tuple_record: int(tuple_record[5]), tuple_records)
    return pd.Series(latencies)

def sink_rx_tuple_ids(f):
    tuple_records = sink_rx_tuples(f)
    return set(map(lambda record: int(record[2]), tuple_records))

def unfinished_sink_tuples(f, t_end, prev_tuples):
    tuple_records = sink_rx_tuples(f)
    print 'Found %d tuple records'%(len(tuple_records))
    filtered_tuple_records = filter(lambda (cnt, tid, ts, txts, rxts, latency, bytez):
            int(rxts) <= int(t_end) and not int(ts) in prev_tuples, tuple_records)
    print 'Left with %d tuple records after filtering'%(len(filtered_tuple_records))
    #total_bytes = reduce(lambda total, (cnt, tid, ts, txts, rxts, latency, bytez): int(bytez)+total, filtered_tuple_records)
    total_bytes = reduce(lambda total, el: int(el[6])+total, filtered_tuple_records, 0)
    return (len(filtered_tuple_records), total_bytes)


def unfinished_sink_rx_latencies(f, t_end):
    tuple_records = sink_rx_tuples(f)
    filtered_tuple_records = filter(lambda (cnt, tid, ts, txts, rxts, latency, bytez):
            int(rxts) <= int(t_end), tuple_records)
    latencies = map(lambda tuple_record: int(tuple_record[5]), filtered_tuple_records)
    return pd.Series(latencies)

def sink_rx_tuples(f):
    results = []
    regex = re.compile(r'SNK: Received tuple with cnt=(\d+),id=(\d+),ts=(\d+),txts=(\d+),rxts=(\d+),latency=(\d+),bytes=(\d+)$')
    for line in f:
        match = re.search(regex, line)
        if match:
            results.append(match.groups())

    return results

def processor_tput(f):
    regex = re.compile(r't=(\d+),id=(\d+),interval=(\d+),tput=(.*),cumTput=(.*)$')

    last_cum_tput = 0
    op_id = None
    for line in f:
        match = re.search(regex, line)
        if match:
            last_cum_tput = float(match.group(5))
            op_id = match.group(2)

    return (op_id, last_cum_tput)
    
