Param (
    [Parameter(HelpMessage = "The Build Number to set in the Version attribute of the files")]
    [Parameter(ParameterSetName = 'bNumber')]
    [Alias("b")]
    [string]$buildNum = "0001",

    [Alias("t")]
    [string]$type = ""
)

if (![string]::IsNullOrEmpty($type))
{
    $type = "-" + $type
}
$buildGradle = [IO.File]::ReadAllText(".\gradle.properties");
$pattern = '${1}' + $buildNum + $type
$buildGradle = $buildGradle -replace "(version\s*=\s*[0-9]*[.][0-9]*[.][0-9]*[.])[0-9]*(\S*)", $pattern
[IO.File]::WriteAllText(".\gradle.properties", $buildGradle)
$pattern = '${1}' + $buildNum + $type
$buildGradle.replace("`n", ", ").replace("`r", ", ") -replace ".*version\s*=\s*([0-9]*[.][0-9]*[.][0-9]*[.])[0-9]*(\S*).*", $pattern

