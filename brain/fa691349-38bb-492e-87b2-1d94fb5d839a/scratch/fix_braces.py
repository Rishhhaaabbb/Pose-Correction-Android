import io

with io.open(r'c:\Users\dines\Music\Pose-Correction-Android\app\src\main\java\com\posemonitor2\MainActivity.kt', 'r', encoding='utf-8') as f:
    lines = f.readlines()

target_line = '                                Text(if (exerciseList.size > 1) "See Summary" else "Done", fontSize = 18.sp, fontWeight = FontWeight.Bold)\n'
found_index = -1
for i, line in enumerate(lines):
    if target_line in line:
        found_index = i
        break

if found_index != -1:
    new_braces = [
        '                                }\n',
        '                            }\n',
        '                        }\n',
        '                    }\n',
        '                }\n',
        '            }\n',
        '        }\n',
        '    }\n',
        '}\n',
        '}\n'
    ]
    # Replace the braces starting from found_index + 1 to 857 (index 856)
    lines[found_index+1 : found_index+11] = new_braces

with io.open(r'c:\Users\dines\Music\Pose-Correction-Android\app\src\main\java\com\posemonitor2\MainActivity.kt', 'w', encoding='utf-8') as f:
    f.writelines(lines)
