# download.py
from huggingface_hub import snapshot_download

print("开始下载 BGE-small-zh 模型...")
snapshot_download(
    repo_id="BAAI/bge-small-zh-v1.5",
    local_dir="./bge-small-zh",
    local_dir_use_symlinks=False,
    ignore_patterns=["*.h5", "*.ot", "*.msgpack"]  # 忽略不需要的文件
)
print("下载完成！")