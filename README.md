# Wiiisdom LogSplitter
## Purpose
LogSplitter is a Wiiisdom 360Eyes log analyzer.
It generates an XLSX workbook with multiple sheets.
Information about 360Eyes is available [here](https://wiiisdom.com/sap-business-objects/audit-impact-analysis-metadata/) 
## Usage
Launch the program as a simple java jar.
```console
java -DlogLevel=info -DpatternFile=C:\files\Patterns.xlsx -jar C:\myJars\LogSplitter-1.0.jar
```

`-DlogLevel=info` (optional) 

`-DpatternFile=C:\files\Patterns.xlsx` (mandatory) Location of the custom pattern file.


A sample .bat file is included for Windows users.
The GUI prompts for an input 360Eyes log file (extension .log), and for an output .XLSX file.

If the output extension is ommitted, it will be added by the program.
## Custom Patterns
