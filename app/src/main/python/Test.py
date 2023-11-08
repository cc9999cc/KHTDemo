import os

def read():
    names = []
    script_dir = os.path.dirname(os.path.abspath(__file__))
    file_path = os.path.join(script_dir, 'facedata/dataset.csv')
    with open(file_path, encoding='UTF-8') as f:
        f.readline()
        for line in f:
            line = line.strip().split(',')
            names.append(line[0])
    return names

