[CmdletBinding()]
param([Parameter(Mandatory)][int]$Port)

Set-StrictMode -Version Latest
$ErrorActionPreference = 'Stop'

$listener = [Net.Sockets.TcpListener]::new([Net.IPAddress]::Loopback, $Port)
$listener.Start()
try {
    while ($true) {
        $client = $listener.AcceptTcpClient()
        try {
            $stream = $client.GetStream()
            $reader = [IO.StreamReader]::new($stream, [Text.Encoding]::ASCII, $false, 4096, $true)
            while ($true) {
                $line = $reader.ReadLine()
                if ($null -eq $line -or $line.Length -eq 0) { break }
            }
            $response = [Text.Encoding]::ASCII.GetBytes("HTTP/1.1 204 No Content`r`nConnection: close`r`nContent-Length: 0`r`n`r`n")
            $stream.Write($response, 0, $response.Length)
            $stream.Flush()
        } finally {
            $client.Dispose()
        }
    }
} finally {
    $listener.Stop()
}
