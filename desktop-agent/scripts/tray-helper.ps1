# AirPad Windows System Tray Helper
# Communicates with Node.js via stdin/stdout line-delimited JSON IPC

[Console]::OutputEncoding = [System.Text.Encoding]::UTF8
[Console]::InputEncoding = [System.Text.Encoding]::UTF8

Add-Type -AssemblyName System.Windows.Forms
Add-Type -AssemblyName System.Drawing

# Create default custom icon in-memory (16x16 or 32x32 clean circle/gamepad icon)
function Create-AirPadIcon([bool]$isConnected) {
    $bmp = New-Object System.Drawing.Bitmap 32, 32
    $g = [System.Drawing.Graphics]::FromImage($bmp)
    $g.SmoothingMode = [System.Drawing.Drawing2D.SmoothingMode]::AntiAlias

    $backColor = if ($isConnected) { [System.Drawing.Color]::FromArgb(46, 204, 113) } else { [System.Drawing.Color]::FromArgb(52, 152, 219) }
    $brush = New-Object System.Drawing.SolidBrush $backColor
    $g.FillEllipse($brush, 2, 2, 28, 28)

    # Draw White "A" inside icon
    $font = New-Object System.Drawing.Font "Arial", 14, [System.Drawing.FontStyle]::Bold
    $textBrush = New-Object System.Drawing.SolidBrush [System.Drawing.Color]::White
    $stringFormat = New-Object System.Drawing.StringFormat
    $stringFormat.Alignment = [System.Drawing.StringAlignment]::Center
    $stringFormat.LineAlignment = [System.Drawing.StringAlignment]::Center
    $rect = New-Object System.Drawing.RectangleF 0, 0, 32, 32
    $g.DrawString("A", $font, $textBrush, $rect, $stringFormat)

    $hIcon = $bmp.GetHicon()
    $icon = [System.Drawing.Icon]::FromHandle($hIcon)

    $g.Dispose()
    $brush.Dispose()
    $font.Dispose()
    $textBrush.Dispose()
    $bmp.Dispose()
    return $icon
}

$trayIcon = New-Object System.Windows.Forms.NotifyIcon
$trayIcon.Icon = Create-AirPadIcon $false
$trayIcon.Text = "AirPad Server - Waiting for phone"
$trayIcon.Visible = $true

$contextMenu = New-Object System.Windows.Forms.ContextMenuStrip

# Menu Items
$statusItem = New-Object System.Windows.Forms.ToolStripMenuItem
$statusItem.Text = "Status: Waiting for phone..."
$statusItem.Enabled = $false
$statusItem.Font = New-Object System.Drawing.Font($statusItem.Font, [System.Drawing.FontStyle]::Bold)
[void]$contextMenu.Items.Add($statusItem)

$pinItem = New-Object System.Windows.Forms.ToolStripMenuItem
$pinItem.Text = "PIN: ------ (Click to Copy)"
$pinItem.Add_Click({
    [Console]::WriteLine('{"event":"COPY_PIN"}')
    [Console]::Out.Flush()
})
[void]$contextMenu.Items.Add($pinItem)

[void]$contextMenu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))

$regenPinItem = New-Object System.Windows.Forms.ToolStripMenuItem
$regenPinItem.Text = "Regenerate PIN"
$regenPinItem.Add_Click({
    [Console]::WriteLine('{"event":"REGENERATE_PIN"}')
    [Console]::Out.Flush()
})
[void]$contextMenu.Items.Add($regenPinItem)

$gamepadItem = New-Object System.Windows.Forms.ToolStripMenuItem
$gamepadItem.Text = "Virtual Gamepad: Enabled"
$gamepadItem.Add_Click({
    [Console]::WriteLine('{"event":"TOGGLE_GAMEPAD"}')
    [Console]::Out.Flush()
})
[void]$contextMenu.Items.Add($gamepadItem)

$disconnectItem = New-Object System.Windows.Forms.ToolStripMenuItem
$disconnectItem.Text = "Disconnect Phone"
$disconnectItem.Visible = $false
$disconnectItem.Add_Click({
    [Console]::WriteLine('{"event":"DISCONNECT"}')
    [Console]::Out.Flush()
})
[void]$contextMenu.Items.Add($disconnectItem)

[void]$contextMenu.Items.Add((New-Object System.Windows.Forms.ToolStripSeparator))

$exitItem = New-Object System.Windows.Forms.ToolStripMenuItem
$exitItem.Text = "Exit AirPad"
$exitItem.Add_Click({
    [Console]::WriteLine('{"event":"EXIT"}')
    [Console]::Out.Flush()
    $trayIcon.Visible = $false
    [System.Windows.Forms.Application]::Exit()
})
[void]$contextMenu.Items.Add($exitItem)

$trayIcon.ContextMenuStrip = $contextMenu

# Double-click tray icon -> copy PIN
$trayIcon.Add_DoubleClick({
    [Console]::WriteLine('{"event":"COPY_PIN"}')
    [Console]::Out.Flush()
})

# Signal readiness to Node.js
[Console]::WriteLine('{"event":"READY"}')
[Console]::Out.Flush()

# Background timer to poll stdin without blocking WinForms message loop
$timer = New-Object System.Windows.Forms.Timer
$timer.Interval = 100
$timer.Add_Tick({
    try {
        if ([Console]::KeyAvailable) {
            # KeyAvailable is not for redirected stdin, so we check Console.In via non-blocking or ReadLine in background thread if needed
        }
    } catch {}
})
$timer.Start()

# Run background thread for reading stdin
$syncHash = [hashtable]::Synchronized(@{
    HasCommand = $false
    CommandJson = ""
    ShouldQuit = $false
})

$scriptBlock = {
    param($sync)
    while ($true) {
        $line = [Console]::In.ReadLine()
        if ($line -eq $null) {
            $sync.ShouldQuit = $true
            break
        }
        $sync.CommandJson = $line
        $sync.HasCommand = $true
    }
}

$thread = [System.Threading.Thread]::new([System.Threading.ParameterizedThreadStart]$scriptBlock)
$thread.IsBackground = $true
$thread.Start($syncHash)

# Timer checks syncHash
$stdinTimer = New-Object System.Windows.Forms.Timer
$stdinTimer.Interval = 50
$stdinTimer.Add_Tick({
    if ($syncHash.ShouldQuit) {
        $trayIcon.Visible = $false
        [System.Windows.Forms.Application]::Exit()
        return
    }

    if ($syncHash.HasCommand) {
        $raw = $syncHash.CommandJson
        $syncHash.HasCommand = $false
        try {
            $cmd = ConvertFrom-Json $raw
            if ($cmd.type -eq "UPDATE") {
                if ($cmd.connected) {
                    $statusItem.Text = "Connected: $($cmd.deviceName)"
                    $disconnectItem.Visible = $true
                    $trayIcon.Text = "AirPad - Connected to $($cmd.deviceName)"
                    $trayIcon.Icon = Create-AirPadIcon $true
                } else {
                    $statusItem.Text = "Status: Waiting for phone..."
                    $disconnectItem.Visible = $false
                    $trayIcon.Text = "AirPad - Waiting for phone"
                    $trayIcon.Icon = Create-AirPadIcon $false
                }

                if ($cmd.pin) {
                    $pinItem.Text = "PIN: $($cmd.pin) (Click to Copy)"
                }

                if ($cmd.PSObject.Properties['gamepad'] -ne $null) {
                    $gpState = if ($cmd.gamepad) { "Enabled" } else { "Disabled" }
                    $gamepadItem.Text = "Virtual Gamepad: $gpState"
                }

                if ($cmd.notifyTitle -and $cmd.notifyMsg) {
                    $trayIcon.ShowBalloonTip(3000, $cmd.notifyTitle, $cmd.notifyMsg, [System.Windows.Forms.ToolTipIcon]::Info)
                }
            } elseif ($cmd.type -eq "QUIT") {
                $trayIcon.Visible = $false
                [System.Windows.Forms.Application]::Exit()
            }
        } catch {}
    }
})
$stdinTimer.Start()

[System.Windows.Forms.Application]::Run()
