# Wiiisdom LogSplitter
## Purpose
LogSplitter is a Wiiisdom 360Eyes log analyzer.<br/>
It generates an XLSX workbook with multiple sheets.<br/>
Information about 360Eyes is available [here](https://wiiisdom.com/sap-business-objects/audit-impact-analysis-metadata/) 
## Usage
Launch the program as a simple java jar.
```console
java -DlogLevel=info -DpatternFile=C:\files\Patterns.xlsx -jar C:\myJars\LogSplitter-1.0.jar
```

`-DlogLevel=<level>` (optional) 

`-DpatternFile=<filepath>` (mandatory) Location of the custom pattern file.

A sample .bat file is included for Windows users.

The GUI prompts for an input 360Eyes log file (extension .log), and for an output .XLSX file.<br/> 
### Input file
An original log file from 360Eyes<br/>
If 360Eyes segmented the file (one .log and one or more .ZIP files), unzip and concatenate them into a single file.
### Output file
If the file already exists, it will be overwritten without warning.<br/>
If the output extension is omitted, it will be added by the program.
## Custom Patterns (RegEx)
The file is mandatory, but it may contain no patterns.<br/>
It is in .xlsx format.<br/>
The first row contains headers, that must be present.<br/>
Subsequent rows, if any have the following structure:
* Column 1: Name of the sheet to be created (must follow Excel rules)
* Column 2: Regular expression that will be applied to each line
* Column 3-n: name for each capturing group in the pattern
	* There must be at least one capturing group in the expression
	* Each name may be prefixed to cause formatting to occur in the resulting column:
		* D_ : Date format. The value MUST be in the format "yyyy-MM-dd HH:mm:ss,SSS" (standard 360Eyes log)
		* I_ : Integer value. It will be formatted with separating commas "#,##0"
		* N_ : Numeric format. It will be formatted with separating commas and two decimals "#,##0.00"
	  * No prefix : General format, i.e. depending on the string value 