# convert_to_onnx.py
# 将 bge-small-zh-v1.5 转换为 ONNX 格式

import torch
from transformers import AutoModel, AutoTokenizer
import os

print("加载模型...")
model_path = "./bge-small-zh"
model = AutoModel.from_pretrained(model_path)
tokenizer = AutoTokenizer.from_pretrained(model_path)

model.eval()

# 创建示例输入
dummy_input = tokenizer("示例文本", return_tensors="pt", padding=True, truncation=True, max_length=512)

print("导出 ONNX...")
output_path = "./bge-small-zh/model.onnx"

torch.onnx.export(
    model,
    (dummy_input["input_ids"], dummy_input["attention_mask"], dummy_input.get("token_type_ids")),
    output_path,
    input_names=["input_ids", "attention_mask", "token_type_ids"],
    output_names=["last_hidden_state", "pooler_output"],
    dynamic_axes={
        "input_ids": {0: "batch", 1: "sequence"},
        "attention_mask": {0: "batch", 1: "sequence"},
        "token_type_ids": {0: "batch", 1: "sequence"},
        "last_hidden_state": {0: "batch", 1: "sequence"},
        "pooler_output": {0: "batch"}
    },
    opset_version=14
)

# 获取文件大小
size_mb = os.path.getsize(output_path) / (1024 * 1024)
print(f"导出完成: {output_path} ({size_mb:.1f} MB)")

# 清理不需要的文件（可选）
print("\n可以删除以下文件节省空间:")
print("  - pytorch_model.bin (~90MB)")
print("  - model.safetensors (~90MB)")
