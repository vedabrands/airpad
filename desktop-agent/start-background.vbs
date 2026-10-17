Set WshShell = CreateObject("WScript.Shell")
' Run AirPad Desktop Agent in background (window hidden 0)
WshShell.CurrentDirectory = CreateObject("Scripting.FileSystemObject").GetParentFolderName(WScript.ScriptFullName)
WshShell.Run "node src/index.js", 0, False
Set WshShell = Nothing
