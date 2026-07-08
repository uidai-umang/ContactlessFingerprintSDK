import cv2
import mediapipe as mp
import numpy as np
from typing import Tuple
from datetime import datetime

class HandDetector:
    def __init__(self, min_detection_confidence=0.7, min_tracking_confidence=0.5):
        print("[LIVENESS] Initializing HandDetector...")
        self.mp_hands = mp.solutions.hands
        self.hands = self.mp_hands.Hands(
            static_image_mode=False,
            max_num_hands=2,
            min_detection_confidence=min_detection_confidence,
            min_tracking_confidence=min_tracking_confidence
        )
        self.mp_draw = mp.solutions.drawing_utils

    def enhance_low_light(self, image: np.ndarray) -> np.ndarray:
        print("[LIVENESS] Applying CLAHE low-light enhancement...")
        lab = cv2.cvtColor(image, cv2.COLOR_BGR2LAB)
        l, a, b = cv2.split(lab)
        clahe = cv2.createCLAHE(clipLimit=3.0, tileGridSize=(8, 8))
        l_enhanced = clahe.apply(l)
        enhanced_lab = cv2.merge([l_enhanced, a, b])
        return cv2.cvtColor(enhanced_lab, cv2.COLOR_LAB2BGR)

    def detect_hands(self, image: np.ndarray) -> Tuple[np.ndarray, list, int]:
        print("[LIVENESS] Converting frame to RGB for Mediapipe...")
        image_rgb = cv2.cvtColor(image, cv2.COLOR_BGR2RGB)
        results = self.hands.process(image_rgb)
        hands_info = []

        if results.multi_hand_landmarks:
            print(f"[LIVENESS] Detected {len(results.multi_hand_landmarks)} hand(s).")
            for idx, (hand_landmarks, handedness) in enumerate(
                zip(results.multi_hand_landmarks, results.multi_handedness)
            ):
                self.mp_draw.draw_landmarks(
                    image, hand_landmarks, self.mp_hands.HAND_CONNECTIONS
                )
                hand_type = handedness.classification[0].label
                finger_count, finger_names = self.detect_fingers(hand_landmarks, hand_type)

                hands_info.append({
                    'hand_type': hand_type,
                    'finger_count': finger_count,
                    'fingers_up': finger_names,
                    "landmarks": [(lm.x, lm.y, lm.z) for lm in hand_landmarks.landmark]
                })

                print(f"[LIVENESS] Hand {idx+1}: {hand_type}, {finger_count} finger(s) up → {', '.join(finger_names)}")

        plus = len(hands_info)
        return image, hands_info, plus

    def detect_fingers(self, hand_landmarks, hand_type) -> Tuple[int, list]:
        finger_tips = [4, 8, 12, 16, 20]
        finger_names = ["Thumb", "Index", "Middle", "Ring", "Pinky"]
        fingers_up = []

        points = [(lm.x, lm.y, lm.z) for lm in hand_landmarks.landmark]
        palm_showing = self.is_palm_showing(hand_landmarks, hand_type)

        # Thumb detection logic
        if hand_type == "Left":
            if palm_showing and points[4][0] > points[3][0]:
                fingers_up.append("Thumb")
            elif not palm_showing and points[4][0] < points[3][0]:
                fingers_up.append("Thumb")
        else:  # Right
            if palm_showing and points[4][0] < points[3][0]:
                fingers_up.append("Thumb")
            elif not palm_showing and points[4][0] > points[3][0]:
                fingers_up.append("Thumb")

        # Other fingers
        for idx, tip in enumerate(finger_tips[1:], start=1):
            if points[tip][1] < points[tip - 2][1]:
                fingers_up.append(finger_names[idx])

        return len(fingers_up), fingers_up

    def is_palm_showing(self, hand_landmarks, hand_type) -> bool:
        wrist = hand_landmarks.landmark[0]
        index_mcp = hand_landmarks.landmark[5]
        pinky_mcp = hand_landmarks.landmark[17]

        wrist_np = np.array([wrist.x, wrist.y, wrist.z])
        index_np = np.array([index_mcp.x, index_mcp.y, index_mcp.z])
        pinky_np = np.array([pinky_mcp.x, pinky_mcp.y, pinky_mcp.z])

        v1 = index_np - wrist_np
        v2 = pinky_np - wrist_np
        normal = np.cross(v1, v2)

        if hand_type == "Right":
            return normal[2] > 0
        else:
            return normal[2] < 0


def process_frame(nv21_bytes: bytes, width: int, height: int, enhance_light: bool = True):
    """
    Processes a single NV21 frame and returns liveness detection results.
    """
    print(f"[LIVENESS] Received NV21 frame: {len(nv21_bytes)} bytes, size {width}x{height}")

    # Convert NV21 → BGR
    yuv = np.frombuffer(nv21_bytes, dtype=np.uint8).reshape((height + height // 2, width))
    bgr = cv2.cvtColor(yuv, cv2.COLOR_YUV2BGR_NV21)

    detector = HandDetector()
    if enhance_light:
        bgr = detector.enhance_low_light(bgr)

    output_frame, hands_info, plus = detector.detect_hands(bgr)

    result = {
        "timestamp": datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        "hands_present": plus > 0,
        "hands_info": hands_info
    }

    print(f"[LIVENESS] Hands present: {result['hands_present']}")
    if result['hands_info']:
        for idx, hand in enumerate(result['hands_info']):
            print(f"[LIVENESS] Hand {idx+1}: {hand['hand_type']} — {hand['finger_count']} fingers")

    return result
