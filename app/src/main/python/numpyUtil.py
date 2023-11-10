import math, numpy as np, random
import os, cv2


def loadAnchors():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    file_path = os.path.join(script_dir, f'facedata/anchors.npy')
    return np.load(file_path)


def loadFaceEncoding():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    file_path = os.path.join(script_dir, f'facedata/face_encodings.npy')
    return np.load(file_path).astype('float')


def loadFaceName():
    script_dir = os.path.dirname(os.path.abspath(__file__))
    file_path = os.path.join(script_dir, f'facedata/face_names.npy')
    strArr = []
    for item in np.load(file_path):
        strArr.append(str(item))
    print(strArr)
    return strArr


def formatImageByPath(imagePath):
    imageRGB = cv2.cvtColor(cv2.imread(imagePath), cv2.COLOR_BGR2RGB)
    return np.array(imageRGB, np.float32)


def formatImage(imageArr, retinaface_input_shape):
    image = np.array(imageArr, np.float32)
    im_height, im_width, _ = np.shape(image)  # 计算image数据各维度的长度
    print("im_height:", im_height, ", im_width:", im_width)
    scale = [
        np.shape(image)[1], np.shape(image)[0], np.shape(image)[1], np.shape(image)[0]
    ]
    scale_for_landmarks = [
        np.shape(image)[1], np.shape(image)[0], np.shape(image)[1], np.shape(image)[0],
        np.shape(image)[1], np.shape(image)[0], np.shape(image)[1], np.shape(image)[0],
        np.shape(image)[1], np.shape(image)[0]
    ]
    image = letterbox_image(image, [retinaface_input_shape[1], retinaface_input_shape[0]])  # 不传播
    # 检测人脸
    return np.array([preprocess_input(image).transpose(2, 0, 1), ], dtype=np.float32).astype('float')


# 辅助函数，做resize的
def letterbox_image(image, size):
    ih, iw, _ = np.shape(image)
    w, h = size
    scale = min(w / iw, h / ih)
    nw = int(iw * scale)
    nh = int(ih * scale)

    image = cv2.resize(image, (nw, nh))
    new_image = np.ones([size[1], size[0], 3]) * 128
    new_image[(h - nh) // 2:nh + (h - nh) // 2, (w - nw) // 2:nw + (w - nw) // 2] = image
    return new_image


# 辅助函数，对RGB值域做量化的
def preprocess_input(image):
    image -= np.array((104, 117, 123), np.float32)
    return image


# 辅助函数，解码人脸区域
def decode(loc, priors, variances):
    boxes = np.concatenate((priors[:, :2] + loc[:, :2] * variances[0] * priors[:, 2:],
                            priors[:, 2:] * np.exp(loc[:, 2:] * variances[1])), 1)
    boxes[:, :2] -= boxes[:, 2:] / 2
    boxes[:, 2:] += boxes[:, :2]
    return boxes


# 辅助函数，解码人脸关键点
def decode_landm(pre, priors, variances):
    landms = np.concatenate((priors[:, :2] + pre[:, :2] * variances[0] * priors[:, 2:],
                             priors[:, :2] + pre[:, 2:4] * variances[0] * priors[:, 2:],
                             priors[:, :2] + pre[:, 4:6] * variances[0] * priors[:, 2:],
                             priors[:, :2] + pre[:, 6:8] * variances[0] * priors[:, 2:],
                             priors[:, :2] + pre[:, 8:10] * variances[0] * priors[:, 2:],
                             ), 1)
    return landms


# 辅助函数，将压缩后图片的人脸区域转化为压缩前的
def retinaface_correct_boxes(result, input_shape, image_shape):
    new_shape = image_shape * np.min(input_shape / image_shape)

    offset = (input_shape - new_shape) / 2. / input_shape
    scale = input_shape / new_shape

    scale_for_boxs = [scale[1], scale[0], scale[1], scale[0]]
    scale_for_landmarks = [scale[1], scale[0], scale[1], scale[0], scale[1], scale[0], scale[1],
                           scale[0], scale[1],
                           scale[0]]

    offset_for_boxs = [offset[1], offset[0], offset[1], offset[0]]
    offset_for_landmarks = [offset[1], offset[0], offset[1], offset[0], offset[1], offset[0],
                            offset[1], offset[0],
                            offset[1], offset[0]]

    result[:, :4] = (result[:, :4] - np.array(offset_for_boxs)) * np.array(scale_for_boxs)
    result[:, 5:] = (result[:, 5:] - np.array(offset_for_landmarks)) * np.array(scale_for_landmarks)

    return result


# 利用人脸坐标把人脸调整到正面。【可不用，但会损失一些准确性】
def Alignment_1(img, landmark):
    if landmark.shape[0] == 68:
        x = landmark[36, 0] - landmark[45, 0]
        y = landmark[36, 1] - landmark[45, 1]
    elif landmark.shape[0] == 5:
        x = landmark[0, 0] - landmark[1, 0]
        y = landmark[0, 1] - landmark[1, 1]
    # 眼睛连线相对于水平线的倾斜角
    if x == 0:
        angle = 0
    else:
        # 计算它的弧度制
        angle = math.atan(y / x) * 180 / math.pi

    center = (img.shape[1] // 2, img.shape[0] // 2)

    RotationMatrix = cv2.getRotationMatrix2D(center, angle, 1)
    # 仿射函数
    new_img = cv2.warpAffine(img, RotationMatrix, (img.shape[1], img.shape[0]))

    RotationMatrix = np.array(RotationMatrix)
    new_landmark = []
    for i in range(landmark.shape[0]):
        pts = []
        pts.append(RotationMatrix[0, 0] * landmark[i, 0] + RotationMatrix[0, 1] * landmark[i, 1] +
                   RotationMatrix[0, 2])
        pts.append(RotationMatrix[1, 0] * landmark[i, 0] + RotationMatrix[1, 1] * landmark[i, 1] +
                   RotationMatrix[1, 2])
        new_landmark.append(pts)

    new_landmark = np.array(new_landmark)

    return new_img, new_landmark


# 辅助函数，计算两个人脸区域的交叠
def iou(b1, b2):
    b1_x1, b1_y1, b1_x2, b1_y2 = b1[0], b1[1], b1[2], b1[3]
    b2_x1, b2_y1, b2_x2, b2_y2 = b2[:, 0], b2[:, 1], b2[:, 2], b2[:, 3]

    inter_rect_x1 = np.maximum(b1_x1, b2_x1)
    inter_rect_y1 = np.maximum(b1_y1, b2_y1)
    inter_rect_x2 = np.minimum(b1_x2, b2_x2)
    inter_rect_y2 = np.minimum(b1_y2, b2_y2)

    inter_area = np.maximum(inter_rect_x2 - inter_rect_x1, 0) * \
                 np.maximum(inter_rect_y2 - inter_rect_y1, 0)

    area_b1 = (b1_x2 - b1_x1) * (b1_y2 - b1_y1)
    area_b2 = (b2_x2 - b2_x1) * (b2_y2 - b2_y1)

    iou = inter_area / np.maximum((area_b1 + area_b2 - inter_area), 1e-6)
    return iou


# 辅助函数，取出重叠小的分数大的区域。
def non_max_suppression(detection, conf_thres=0.5, nms_thres=0.3):
    # 1、找出该图片中得分大于门限函数的框。在进行重合框筛选前就进行得分的筛选可以大幅度减少框的数量。
    mask = detection[:, 4] >= conf_thres
    detection = detection[mask]
    if not np.shape(detection)[0]:
        return []

    best_box = []
    scores = detection[:, 4]
    # 2、根据得分对框进行从大到小排序。
    arg_sort = np.argsort(scores)[::-1]
    detection = detection[arg_sort]

    while np.shape(detection)[0] > 0:
        # 3、每次取出得分最大的框，计算其与其它所有预测框的重合程度，重合程度过大的则剔除。
        best_box.append(detection[0])
        if len(detection) == 1:
            break
        ious = iou(best_box[-1], detection[1:])
        detection = detection[1:][ious < nms_thres]

    return np.array(best_box)
