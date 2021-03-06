#!/usr/bin/env python
# Python 3
import shlex
import subprocess
import re
import os
import time

# Variables
HOME = os.environ.get("HOME")
HADOOP_CMD = "/opt/hadoop/bin/hadoop"
PRG_NAME = HOME + "/Dropbox/GPGPU-on-Hadoop/Jars/KMeans/KMeansHadoop.jar"    # TODO to set
DATA_SIZES = ["16", "32", "64"]    # TODO to set
DIM_SIZES = ["2", "64", "256"]    # TODO to set
MODES = ["cpu", "ocl"]  # TODO to set
RUNS = 3    # TODO to set
ITERATIONS = "1"    # TODO to set
LOG_PATH = "/tmp/km_logs"
DATA_PATH = HOME + "/Documents/km_data"
HDFS_INPUT = "/km_data/input"
HDFS_CENTROIDS = "/km_data/centroids"
HDFS_OUTPUT = "/km_data/output"

# Functions
def copyHdfs(data, dim):
    command = HADOOP_CMD + " fs -mkdir " + HDFS_INPUT
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    command = HADOOP_CMD + " fs -put " + DATA_PATH + "/" + "points_" + data + "mb_" + dim + "d" + " " + HDFS_INPUT + "/points"
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    command = HADOOP_CMD + " fs -mkdir " + HDFS_CENTROIDS
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    # TODO
    command = HADOOP_CMD + " fs -put " + DATA_PATH + "/" + "center_" + data + "mb_" + dim + "d" + " " + HDFS_CENTROIDS + "/center"
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    
def clearHdfs():
    command = HADOOP_CMD + " fs -rmr " + HDFS_INPUT
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    command = HADOOP_CMD + " fs -rmr " + HDFS_CENTROIDS
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    command = HADOOP_CMD + " fs -rmr " + HDFS_OUTPUT
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    
def clearIntermediateHdfs():
    command = HADOOP_CMD + " fs -rmr " + HDFS_OUTPUT
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()
    command = HADOOP_CMD + " fs -rmr " + HDFS_CENTROIDS + "-*"
    args = shlex.split(command)
    p = subprocess.Popen(args, stdout=subprocess.PIPE)
    p.wait()

# Print information
print('Home:', HOME)
print('Hadoop:', HADOOP_CMD)
print('Program:', PRG_NAME)
print('Log path:', LOG_PATH)
print('HDFS input:', HDFS_INPUT)
print('HDFS centroids:', HDFS_CENTROIDS)
print('HDFS output:', HDFS_OUTPUT)
print()

# Create log path
command = "mkdir " + LOG_PATH
args = shlex.split(command)
p = subprocess.Popen(args, stdout=subprocess.PIPE)
p.wait()

# Run tests
print('Start runs ...')

for data in DATA_SIZES:
    print('    data size: ', data)
    
    for dim in DIM_SIZES:
        print('    dim size: ', dim)
        # prepare HDFS
        print("    Clear HDFS ...")
        clearHdfs()
        print("    Clear HDFS finished!")
        print("    Copy data to HDFS ...")
        copyHdfs(data, dim)
        print("    Copy data HDFS finished!")
        print("    Waiting 10 seconds for duplication!")
        time.sleep(10)
        
        for mode in MODES:
            print('    mode: ', mode)
            # prepare command to start
            jobName = "KMeans_" + data + "mb_" + dim + "d_" + mode
            command = HADOOP_CMD + " jar " + PRG_NAME + " " + jobName + " " + HDFS_INPUT + " " + HDFS_CENTROIDS + " " + HDFS_OUTPUT + " "  + mode + " "  + ITERATIONS   # TODO to set
            print('    command: ', command)
            args = shlex.split(command)
            # TODO start job
            file = open(LOG_PATH + "/" + jobName, 'w+b')
            
            for run in range(0, RUNS):
                print("    Starting run #", run)
                # clear intermediate data
                print("    Clear intermediate HDFS data ...")
                clearIntermediateHdfs()
                print("    Clear intermediate HDFS data finished!")
                # run
                p = subprocess.Popen(args, stdout=subprocess.PIPE)
                p.wait()
                
                file.write(p.stdout.read())
                print("    Finished run #", run)
                
            file.close()
            print()
            
        print()
        
    print()
    
print('finished runs!') 
