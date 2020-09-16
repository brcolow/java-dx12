$jdk = "C:\Users\mikee\dev\panama-foreign\build\windows-x86_64-server-release\jdk"
nal -Name jextract -Value "$jdk\bin\jextract.exe"
$I = "C:\Program Files (x86)\Windows Kits\10\Include\10.0.19041.0"
# This does nothing because Windows.h is purely includes and we are using --filter, but according to Wikipedia:
# Many of these files cannot simply be included by themselves (they are not self-contained), because of dependencies.
# So this will need to be trial and error on what we need. We definitely need the CreateWindowEx function.
jextract --source -d . -t com.dx12 -I "$I\um" -I "$I\shared" -C "-DWIN32_LEAN_AND_MEAN" -- "$I\um\Windows.h"
# jextract --source -d . -t com.dx12 -I "$I\shared" -I "$I\um" -C "-DWIN32_LEAN_AND_MEAN=1" -C "-D_AMD64_=1" -- "$I\um\WinUser.h"
jextract --source -d . -t com.dx12 -I "$I\um" --filter "d3d12.h" -- "$I\um\d3d12.h"
jextract --source -d . -t com.dx12 -I "$I\um" --filter "d3dcommon.h" -- "$I\um\d3dcommon.h"
jextract --source -d . -t com.dx12 -I "$I\um" --filter "dxgi.h" -- "$I\shared\dxgi.h"

