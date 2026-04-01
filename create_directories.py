import os

directories = [
    r'd:\changyong\Andriod\Diaryd\app\src\main\java\com\xinkong\diary\rag\embedding',
    r'd:\changyong\Andriod\Diaryd\app\src\main\java\com\xinkong\diary\rag\vector',
    r'd:\changyong\Andriod\Diaryd\app\src\main\java\com\xinkong\diary\rag\index',
    r'd:\changyong\Andriod\Diaryd\app\src\main\java\com\xinkong\diary\rag\search',
    r'd:\changyong\Andriod\Diaryd\app\src\main\assets\models'
]

for directory in directories:
    os.makedirs(directory, exist_ok=True)
    print(f"Created/verified: {directory}")

print("\nAll directories created successfully!")
