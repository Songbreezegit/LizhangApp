"""AI Studio 异步 OCR 脱敏示例。

真实令牌只允许通过环境变量提供；本文件可安全提交。
"""
import json
import os
import time

import requests

JOB_URL = "https://paddleocr.aistudio-app.com/api/v2/ocr/jobs"
TOKEN = os.environ["AISTUDIO_ACCESS_TOKEN"]
MODEL = os.environ.get("AISTUDIO_OCR_MODEL", "PaddleOCR-VL-1.6")
FILE_PATH = os.environ["AISTUDIO_TEST_FILE"]
HEADERS = {"Authorization": f"bearer {TOKEN}"}
OPTIONS = {
    "useDocOrientationClassify": False,
    "useDocUnwarping": False,
    "useChartRecognition": False,
}

with open(FILE_PATH, "rb") as image:
    response = requests.post(
        JOB_URL,
        headers=HEADERS,
        data={"model": MODEL, "optionalPayload": json.dumps(OPTIONS)},
        files={"file": image},
        timeout=60,
    )
response.raise_for_status()
job_id = response.json()["data"]["jobId"]

while True:
    response = requests.get(f"{JOB_URL}/{job_id}", headers=HEADERS, timeout=30)
    response.raise_for_status()
    data = response.json()["data"]
    if data["state"] == "done":
        result_response = requests.get(data["resultUrl"]["jsonUrl"], timeout=60)
        result_response.raise_for_status()
        print(result_response.text)
        break
    if data["state"] == "failed":
        raise RuntimeError("OCR 任务失败")
    time.sleep(5)
