package com.dx12;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.CLinker;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;
import jdk.incubator.foreign.ValueLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

import static com.dx12.d3d12sdklayers_h.ID3D12Debug;
import static com.dx12.d3d12sdklayers_h.ID3D12DebugVtbl;
import static com.dx12.d3d12_h.D3D12CreateDevice;
import static com.dx12.d3d12_h.D3D12GetDebugInterface;
import static com.dx12.d3d12_h.ID3D12DescriptorHeap;
import static com.dx12.d3d12_h.D3D12_COMMAND_LIST_TYPE_DIRECT;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_DESC;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_FLAG_NONE;
import static com.dx12.d3d12_h.ID3D12CommandQueue;
import static com.dx12.d3d12_h.ID3D12Resource;
import static com.dx12.d3d12_h.ID3D12Device5;
import static com.dx12.d3d12_h.ID3D12Device5Vtbl;
import static com.dx12.d3d12_h.D3D12_DESCRIPTOR_HEAP_DESC;
import static com.dx12.d3d12_h.D3D12_CPU_DESCRIPTOR_HANDLE;
import static com.dx12.d3d12_h.ID3D12DescriptorHeapVtbl;
import static com.dx12.dxgi1_3_h.CreateDXGIFactory2;
import static com.dx12.dxgi_h.DXGI_ADAPTER_DESC1;
import static com.dx12.dxgi1_2_h.DXGI_SWAP_CHAIN_DESC1;
import static com.dx12.dxgi1_2_h.IDXGISwapChain1;
import static com.dx12.dxgi1_2_h.IDXGISwapChain1Vtbl;

import static com.dx12.dxgi1_5_h.IDXGIFactory5;
import static com.dx12.dxgi1_6_h.IDXGIAdapter4;
import static com.dx12.dxgi1_6_h.IDXGIAdapter4Vtbl;

import static jdk.incubator.foreign.CLinker.C_CHAR;
import static jdk.incubator.foreign.CLinker.C_INT;
import static jdk.incubator.foreign.CLinker.C_LONG;
import static jdk.incubator.foreign.CLinker.C_LONGLONG;
import static jdk.incubator.foreign.CLinker.C_POINTER;
import static jdk.incubator.foreign.CLinker.C_SHORT;
import static jdk.incubator.foreign.CLinker.getInstance;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;

/**
 * https://github.com/Meith/DX12/tree/master/DX12
 * <p>
 * https://cr.openjdk.java.net/~mcimadamore/panama/ffi.html#appendix-full-source-code
 */
public class DX12 {
    public static MemoryAddress createWindow(NativeScope scope) {
        MemorySegment pWindowClass = tagWNDCLASSEXW.allocate(scope);
        tagWNDCLASSEXW.cbSize$set(pWindowClass, (int) tagWNDCLASSEXW.sizeof());
        tagWNDCLASSEXW.style$set(pWindowClass, CS_HREDRAW() | CS_VREDRAW());
        // https://stackoverflow.com/questions/25341565/how-do-i-obtain-the-hinstance-for-the-createwindowex-function-when-using-it-outs
        tagWNDCLASSEXW.hInstance$set(pWindowClass, MemoryAddress.NULL);
        tagWNDCLASSEXW.hCursor$set(pWindowClass, LoadCursorW(MemoryAddress.NULL, IDC_ARROW()));

        MethodHandle winProcHandle;
        try {
            winProcHandle = MethodHandles.lookup()
                    .findStatic(WindowProc.class, "WindowProc",
                            MethodType.methodType(long.class, MemoryAddress.class, int.class, long.class, long.class));
        } catch (NoSuchMethodException | IllegalAccessException ex) {
            ex.printStackTrace();
            throw new RuntimeException(ex);
        }
        MemorySegment winProcFunc = CLinker.getInstance().upcallStub(winProcHandle, WindowProc);
        tagWNDCLASSEXW.lpfnWndProc$set(pWindowClass, winProcFunc.address());
        MemoryAddress windowName = CLinker.toCString("JavaDX12Win", StandardCharsets.UTF_16LE).address();
        tagWNDCLASSEXW.lpszClassName$set(pWindowClass, windowName);
        short atom = RegisterClassExW(pWindowClass.address());
        if (atom == 0) {
            System.out.println("RegisterClassExW failed!");
            System.out.println("Error: " + GetLastError());
            System.exit(-1);
        }
        System.out.println("RegisterClassExW return: " + atom);
        MemoryAddress hwndMain = CreateWindowExW(0, windowName,
                CLinker.toCString("My Window", StandardCharsets.UTF_16LE).address(), WS_OVERLAPPEDWINDOW(), CW_USEDEFAULT(),
                CW_USEDEFAULT(), 800, 600, MemoryAddress.NULL, MemoryAddress.NULL, MemoryAddress.NULL, MemoryAddress.NULL);
        if (hwndMain == MemoryAddress.NULL) {
            System.out.println("CreateWindowExW failed!");
            System.exit(-1);
        }
        System.out.println("hwndMain: " + hwndMain);
        ShowWindow(hwndMain, SW_SHOW());
        UpdateWindow(hwndMain);
        return hwndMain;
    }

    public static void main(String[] args) throws Throwable {
        // TODO: Create a utility to check what Windows version we are running on, and then what Windows versions
        //  introduced which version of the various class like ID3D12Device1, 2, 3, etc.
        //  https://docs.microsoft.com/en-us/windows/win32/api/d3d12/ne-d3d12-d3d12_feature

        // TODO: Use an app manifest to target Windows 10. If we are lucky we can do this with jlink...MAYBE...
        //  need to see if jlink calls link.exe and if so how we can set arguments:
        //  https://docs.microsoft.com/en-us/cpp/build/reference/manifestinput-specify-manifest-input?view=vs-2019
        //  https://docs.microsoft.com/en-us/windows/win32/w8cookbook/application--executable--manifest
        //  https://github.com/rust-lang/rfcs/issues/721
        LibraryLookup user32 = LibraryLookup.ofLibrary("user32");
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        LibraryLookup dxgi = LibraryLookup.ofLibrary("dxgi");
        try (NativeScope scope = NativeScope.unboundedScope()) {
            MemoryAddress hwndMain = createWindow(scope);
            var ppvDebug = ID3D12Debug.allocatePointer(scope);
            checkResult("D3D12GetDebugInterface", D3D12GetDebugInterface(IID.IID_ID3D12Debug.guid, ppvDebug));
            // This one is trickier because it doesn't take a pp that it sets, it just does an action - need to make
            // a different signature
            //callMethodOnVTable(ppvDebug, ID3D12Debug.class, MethodType.methodType(int.class, MemoryAddress.class), "EnableDebugLayer");
            MemorySegment pvDebug = asSegment(MemoryAccess.getAddress(ppvDebug), ID3D12Debug.$LAYOUT());
            MemorySegment vDebugVtbl = asSegment(ID3D12Debug.lpVtbl$get(pvDebug), ID3D12DebugVtbl.$LAYOUT());
            MemoryAddress enableDebugLayerAddr = ID3D12DebugVtbl.EnableDebugLayer$get(vDebugVtbl);
            MethodHandle enableDebugLayerMethodHandle = getInstance().downcallHandle(
                    enableDebugLayerAddr,
                    MethodType.methodType(int.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER));
            checkResult("EnableDebugLayer", (int) enableDebugLayerMethodHandle.invokeExact(pvDebug.address()));

            // IDXGIFactory1** dxgiFactory;
            var ppDxgiFactory = IDXGIFactory5.allocatePointer(scope);
            // HRESULT = CreateDXGIFactory1(_uuid(dxgiFactory), &dxgiFactory))
            checkResult("CreateDXGIFactory2", CreateDXGIFactory2(DXGI_CREATE_FACTORY_DEBUG(), IID.IID_IDXGIFactory5.guid, ppDxgiFactory));
            var result = callClassMethod(IDXGIFactory5.class, ppDxgiFactory, "EnumAdapters1",
                    IDXGIAdapter4.class, scope,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class), 0);
            var ppAdapter = result.ppOut;
            //var pDxgiFactory = result.pThis;
            //var dxgiFactoryVtbl = result.pThisVtbl;
            /*
            // IDXGIFactory1*
            MemorySegment pDxgiFactory = asSegment(MemoryAccess.getAddress(ppDxgiFactory), IDXGIFactory5.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment dxgiFactoryVtbl = asSegment(IDXGIFactory5.lpVtbl$get(pDxgiFactory), IDXGIFactory5Vtbl.$LAYOUT());
            // lpVtbl->EnumAdapters1
            MemoryAddress enumAdaptersAddr = IDXGIFactory5Vtbl.EnumAdapters1$get(dxgiFactoryVtbl);

            // link the pointer
            MethodHandle EnumAdapters1MethodHandle = getInstance().downcallHandle(
                    enumAdaptersAddr,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER));

            // [annotation][out] _COM_Outptr_  IDXGIAdapter4**
            MemorySegment ppOut = IDXGIAdapter4.allocatePointer(scope);
            checkResult("EnumAdapters1", (int) EnumAdapters1MethodHandle.invokeExact(pDxgiFactory.address(), 0, ppOut.address()));

             */
            // IDXGIAdapter1*
            MemorySegment pAdapter = asSegment(MemoryAccess.getAddress(ppAdapter), IDXGIAdapter4.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment dxgiAdapterVtbl = asSegment(IDXGIAdapter4.lpVtbl$get(pAdapter), IDXGIAdapter4Vtbl.$LAYOUT());
            // lpVtbl->EnumAdapters1
            // HRESULT(*)(IDXGIAdapter1*,DXGI_ADAPTER_DESC1*)
            MemoryAddress getDesc1Addr = IDXGIAdapter4Vtbl.GetDesc1$get(dxgiAdapterVtbl);

            // link the pointer
            MethodHandle getDesc1MethodHandle = getInstance().downcallHandle(
                    getDesc1Addr,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

            /* DXGI_ADAPTER_DESC1* */
            MemorySegment pDesc = DXGI_ADAPTER_DESC1.allocate(scope);
            checkResult("getDesc1", (int) getDesc1MethodHandle.invokeExact(pAdapter.address(), pDesc.address()));

            // print description
            MemorySegment descStr = DXGI_ADAPTER_DESC1.Description$slice(pDesc);
            String str = new String(descStr.toByteArray(), StandardCharsets.UTF_16LE);
            System.out.println(str);

            // ID3D12Device** d3d12Device;
            var ppDevice = ID3D12Device5.allocatePointer(scope);

            // D3D12CreateDevice(pAdapter, D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&ppDevice))
            checkResult("D3D12CreateDevice", D3D12CreateDevice(pAdapter, D3D_FEATURE_LEVEL_11_0(), IID.IID_ID3D12Device5.guid, ppDevice));
            MemorySegment pQueueDesc = D3D12_COMMAND_QUEUE_DESC.allocate(scope);
            D3D12_COMMAND_QUEUE_DESC.Type$set(pQueueDesc, D3D12_COMMAND_LIST_TYPE_DIRECT());
            D3D12_COMMAND_QUEUE_DESC.Flags$set(pQueueDesc, D3D12_COMMAND_QUEUE_FLAG_NONE());
            result = callClassMethod(ID3D12Device5.class, ppDevice, "CreateCommandQueue",
                    ID3D12CommandQueue.class, scope,
                    MethodType.methodType(
                            int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    pQueueDesc.address(), IID.IID_ID3D12CommandQueue.guid.address());
            var pDevice = result.pThis;
            var deviceVtbl = result.pThisVtbl;
            var ppQueue = result.ppOut;

            MemorySegment pSwapChainDesc = DXGI_SWAP_CHAIN_DESC1.allocate(scope);
            DXGI_SWAP_CHAIN_DESC1.Width$set(pSwapChainDesc, 0);
            DXGI_SWAP_CHAIN_DESC1.Height$set(pSwapChainDesc, 0);
            DXGI_SWAP_CHAIN_DESC1.Format$set(pSwapChainDesc, DXGI_FORMAT_B8G8R8A8_UNORM());
            DXGI_SWAP_CHAIN_DESC1.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("SampleDesc"),
                    MemoryLayout.PathElement.groupElement("Count")).set(pSwapChainDesc, 1);
            DXGI_SWAP_CHAIN_DESC1.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("SampleDesc"),
                    MemoryLayout.PathElement.groupElement("Quality")).set(pSwapChainDesc, 0);
            DXGI_SWAP_CHAIN_DESC1.BufferUsage$set(pSwapChainDesc, DXGI_USAGE_RENDER_TARGET_OUTPUT());
            DXGI_SWAP_CHAIN_DESC1.BufferCount$set(pSwapChainDesc, 2);
            DXGI_SWAP_CHAIN_DESC1.SwapEffect$set(pSwapChainDesc, DXGI_SWAP_EFFECT_FLIP_DISCARD());
            DXGI_SWAP_CHAIN_DESC1.AlphaMode$set(pSwapChainDesc, DXGI_ALPHA_MODE_UNSPECIFIED());
            DXGI_SWAP_CHAIN_DESC1.Scaling$set(pSwapChainDesc, DXGI_SCALING_STRETCH());
            DXGI_SWAP_CHAIN_DESC1.Stereo$set(pSwapChainDesc, 0);
            MemorySegment pQueue = asSegment(MemoryAccess.getAddress(ppQueue), ID3D12CommandQueue.$LAYOUT());

            // FIXME: We should be passing in dxgiFactoryVtbl instead of creating a new one? Also passing in pDxgiFactory?
            result = callClassMethod(IDXGIFactory5.class, ppDxgiFactory, "CreateSwapChainForHwnd",
                    IDXGISwapChain1.class, scope,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class,
                            MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    pQueue.address(), hwndMain.address(), pSwapChainDesc.address(), MemoryAddress.NULL, MemoryAddress.NULL);
            var ppSwapChain = result.ppOut;
            /*
            MemoryAddress addrCreateSwapChainForHwnd = IDXGIFactory5Vtbl.CreateSwapChainForHwnd$get(dxgiFactoryVtbl);
            MethodHandle createSwapChainForHwndMethodHandle = getInstance().downcallHandle(
                    addrCreateSwapChainForHwnd,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class,
                            MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(
                            C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
            MemorySegment ppSwapChain = IDXGISwapChain1.allocatePointer(scope);
            MemorySegment pQueue = asSegment(MemoryAccess.getAddress(ppQueue), ID3D12CommandQueue.$LAYOUT());

            checkResult("createSwapChainForHwnd", (int) createSwapChainForHwndMethodHandle.invokeExact(
                    pDxgiFactory.address(),
                    pQueue.address(),
                    hwndMain.address(),
                    pSwapChainDesc.address(), MemoryAddress.NULL, MemoryAddress.NULL,
                    ppSwapChain.address()));
             */

            // https://github.com/microsoft/DirectX-Graphics-Samples/blob/master/Samples/Desktop/D3D12HelloWorld/src/HelloWindow/D3D12HelloWindow.cpp
            /*
                D3D12_DESCRIPTOR_HEAP_DESC rtvHeapDesc = {};
                rtvHeapDesc.NumDescriptors = FrameCount;
                rtvHeapDesc.Type = D3D12_DESCRIPTOR_HEAP_TYPE_RTV;
                rtvHeapDesc.Flags = D3D12_DESCRIPTOR_HEAP_FLAG_NONE;
                ThrowIfFailed(m_device->CreateDescriptorHeap(&rtvHeapDesc, IID_PPV_ARGS(&m_rtvHeap)));
             */
            MemorySegment pHeapDesc = D3D12_DESCRIPTOR_HEAP_DESC.allocate(scope);
            D3D12_DESCRIPTOR_HEAP_DESC.NumDescriptors$set(pHeapDesc, 1);
            D3D12_DESCRIPTOR_HEAP_DESC.Type$set(pHeapDesc, D3D12_DESCRIPTOR_HEAP_TYPE_RTV());
            D3D12_DESCRIPTOR_HEAP_DESC.Flags$set(pHeapDesc, D3D12_DESCRIPTOR_HEAP_FLAG_NONE());
            result = callClassMethod(ID3D12Device5.class, ppDevice, "CreateDescriptorHeap",
                    ID3D12DescriptorHeap.class, scope,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    pHeapDesc.address(), IID.IID_ID3D12DescriptorHeap.guid.address());
            var ppHeapDescriptor = result.ppOut;
            var pHeapDescriptor = asSegment(MemoryAccess.getAddress(ppHeapDescriptor), ID3D12DescriptorHeap.$LAYOUT());

            /*
            MemoryAddress createDescriptorHeapAddr = ID3D12Device5Vtbl.CreateDescriptorHeap$get(deviceVtbl);
            MethodHandle MH_ID3D12Device_CreateDescriptorHeap = getInstance().downcallHandle(
                    createDescriptorHeapAddr,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));
            var ppHeapDescriptor = ID3D12DescriptorHeap.allocatePointer(scope);
            checkResult("ID3D12Device_CreateDescriptorHeap", (int) MH_ID3D12Device_CreateDescriptorHeap.invokeExact(
                    pDevice.address(), pHeapDesc.address(),
                    IID.IID_ID3D12DescriptorHeap.guid.address(), ppHeapDescriptor.address()));


             */
            MemorySegment heapDescriptorVtbl = asSegment(ID3D12DescriptorHeap.lpVtbl$get(pHeapDescriptor), ID3D12DescriptorHeapVtbl.$LAYOUT());
            MemoryAddress getCPUDescriptorHandleForHeapStartAddr = ID3D12DescriptorHeapVtbl.GetCPUDescriptorHandleForHeapStart$get(heapDescriptorVtbl);
            //MemoryAddress getCPUDescriptorHandleForHeapStartAddr = (MemoryAddress) MemoryHandles.asAddressVarHandle(ID3D12DescriptorHeapVtbl.$LAYOUT().varHandle(long.class, MemoryLayout.PathElement.groupElement("GetCPUDescriptorHandleForHeapStart"))).get(heapDescriptorVtbl);
            var pRtvHandle = D3D12_CPU_DESCRIPTOR_HANDLE.allocate(scope);
            MethodHandle MH_ID3D12DescriptorHeap_GetCPUDescriptorHandleForHeapStart = getInstance().downcallHandle(
                    getCPUDescriptorHandleForHeapStartAddr,
                    MethodType.methodType(void.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.ofVoid(C_POINTER, C_POINTER));
            // Even though GetCPUDescriptorHandleForHeapStart is documented as taking no arguments (except for the
            // this pointer), to use from C code it needs to take an additional rtvHandle argument, see, for example:
            // https://joshstaiger.org/notes/C-Language-Problems-in-Direct3D-12-GetCPUDescriptorHandleForHeapStart.html
            // https://github.com/curldivergence/dx12bindings/blob/841974943b7fbbd146d5372e9b496a9d72daf771/build.rs#L33
             MH_ID3D12DescriptorHeap_GetCPUDescriptorHandleForHeapStart.invokeExact(
                    pHeapDescriptor.address(), pRtvHandle.address());
            //System.out.println("ID3D12DescriptorHeap_GetCPUDescriptorHandleForHeapStart result: " + descriptorHandleStart);
            //var rtvHandle = asSegment(pRtvHandle.address(), D3D12_CPU_DESCRIPTOR_HANDLE.$LAYOUT());
            long pRtvHandlePtr = D3D12_CPU_DESCRIPTOR_HANDLE.ptr$get(pRtvHandle);
            System.out.println("pRtvHandlePtr: " + pRtvHandlePtr);
            //long rtvHandlePtr = D3D12_CPU_DESCRIPTOR_HANDLE.ptr$get(rtvHandle);
            //System.out.println("rtvHandlePtr: " + rtvHandlePtr);

            //System.out.println("ptr: " + D3D12_CPU_DESCRIPTOR_HANDLE.ptr$get(pRtvHandle));
            //System.out.println("rtvHandle: " + rtvHandle);

            MemoryAddress getDescriptorHandleIncrementSizeAddr = ID3D12Device5Vtbl.GetDescriptorHandleIncrementSize$get(deviceVtbl);
            MethodHandle MH_ID3D12Device_GetDescriptorHandleIncrementSize = getInstance().downcallHandle(
                    getDescriptorHandleIncrementSizeAddr,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_INT));
            int rtvDescriptorSize = (int) MH_ID3D12Device_GetDescriptorHandleIncrementSize.invoke(
                    pDevice.address(), D3D12_DESCRIPTOR_HEAP_TYPE_RTV());
            System.out.println("rtvDescriptorSize: " + rtvDescriptorSize);

            MemorySegment pSwapChain = asSegment(MemoryAccess.getAddress(ppSwapChain), IDXGISwapChain1.$LAYOUT());
            MemorySegment swapChainVtbl = asSegment(IDXGISwapChain1.lpVtbl$get(pSwapChain), IDXGISwapChain1Vtbl.$LAYOUT());
            MemoryAddress addrGetBuffer = IDXGISwapChain1Vtbl.GetBuffer$get(swapChainVtbl);
            MethodHandle getBufferMethodHandle = getInstance().downcallHandle(
                    addrGetBuffer,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(
                            C_INT, C_POINTER, C_INT, C_POINTER, C_POINTER));
            var ppSurface0 = ID3D12Resource.allocatePointer(scope);
            checkResult("IDXGISwapChain1_GetBuffer", (int) getBufferMethodHandle.invokeExact(
                    pSwapChain.address(), 0,
                    IID.IID_ID3D12Resource.guid.address(), ppSurface0.address()));
            var pSurface0 = asSegment(ppSurface0.address(), ID3D12Resource.$LAYOUT());

            MemoryAddress createRenderTargetViewAddr = ID3D12Device5Vtbl.CreateRenderTargetView$get(deviceVtbl);
            MethodHandle MH_ID3D12Device_CreateRenderTargetView = getInstance().downcallHandle(
                    createRenderTargetViewAddr,
                    MethodType.methodType(void.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.ofVoid(C_POINTER, C_POINTER, C_POINTER, C_POINTER));
            // [10300] D3D12 ERROR: ID3D12Device::CreateRenderTargetView: Specified CPU descriptor handle
            // ptr=0xFFFFFFFFDE347210 does not refer to a location in a descriptor heap.
            // [ EXECUTION ERROR #646: INVALID_DESCRIPTOR_HANDLE]
            //MemorySegment rtvHandle = D3D12_CPU_DESCRIPTOR_HANDLE.allocate(scope);
            //D3D12_CPU_DESCRIPTOR_HANDLE.ptr$set(rtvHandle, pRtvHandlePtr + rtvDescriptorSize);
            MH_ID3D12Device_CreateRenderTargetView.invokeExact(pDevice.address(), pSurface0.address(), MemoryAddress.NULL, pRtvHandle.address());
            // mDevice->lpVtbl->CreateRenderTargetView(mDevice, mRenderTarget[n], NULL, rtvHandle);
            // rtvHandle.ptr += mrtvDescriptorIncrSize;
            //
            // then we "create" a render target view which binds the swap chain buffer (ID3D12Resource[n]) to the rtv handle
            // device->CreateRenderTargetView(renderTargets[i], nullptr, rtvHandle);
            //
            //  we increment the rtv handle by the rtv descriptor size we got above
            //  rtvHandle.Offset(1, rtvDescriptorSize);


            ScheduledExecutorService executorService = Executors.newScheduledThreadPool(1);
            executorService.schedule(() -> {}, 1, TimeUnit.MINUTES);
        }
    }

    public static class VTableMethodResult {
        MemorySegment ppThis;
        MemorySegment pThis;
        MemorySegment pThisVtbl;
        MemorySegment ppOut;

        public VTableMethodResult(MemorySegment ppThis, MemorySegment pThis, MemorySegment pThisVtbl, MemorySegment ppOut) {
            this.ppThis = ppThis;
            this.pThis = pThis;
            this.pThisVtbl = pThisVtbl;
            this.ppOut = ppOut;
        }
    }

    /**
     * This method uses reflection and thus may be too slow to use. Benchmark this.
     *
     * Helper method that makes it easier to call the C++ equivalent of (pThis)->someMethod(ppOut) which, because
     * we are actually using the DirectX 12 C interface is done using vtable structs and it takes quite a lot of
     * boilerplate.
     * <p>
     * Consider the following C++ code that is typical of DirectX 12:
     *
     * {@code
     * IDXGIAdapter1 * pAdapter;
     * pFactory->EnumAdapters1(0, &pAdapter)
     * }
     *
     * This type of class method structure is emulated in C via the following API:
     *
     * {@code
     * 	HRESULT ( STDMETHODCALLTYPE *EnumAdapters1 )(
     * 		IDXGIFactory1 * This,
     * 		// [in] UINT Adapter,
     *      // [out] IDXGIAdapter1 **ppAdapter);
     * }
     *
     * Which must be called like so:
     *
     * {@code factory->lpVtbl->EnumAdapters1(factory, 0, &adapter)}
     *
     * To replicate this in Panama requires quite a bit of fiddling, so this method would be used thusly:
     *
     * {@code
     * var ppDxgiFactory = IDXGIFactory5.allocatePointer(scope);
     * var result = callMethodOnVTable(scope, ppDxgiFactory, IDXGIFactory5.class, IDXGIAdapter4.class,
     *      MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class), "EnumAdapters1", 0);
     * }
     */
    private static <T, V> VTableMethodResult callClassMethod(Class<T> pThisType, MemorySegment ppThis,
                                                             String methodName,
                                                             Class<V> pOutType, NativeScope scope,
                                                             MethodType methodType,
                                                             Object... inArgs) throws IllegalArgumentException {
        try {
            MemorySegment pThis = asSegment(MemoryAccess.getAddress(ppThis),
                    (MemoryLayout) pThisType.getMethod("$LAYOUT").invoke(null));
            Class<?> vtblClazz = Class.forName(pThisType.getName() + "Vtbl");
            MemorySegment pVtbl = asSegment((MemoryAddress) pThisType.getMethod("lpVtbl$get", MemorySegment.class)
                            .invoke(null, pThis),
                    (MemoryLayout) vtblClazz.getMethod("$LAYOUT").invoke(null));
            MemoryAddress methodAddr = (MemoryAddress) vtblClazz.getMethod(methodName + "$get", MemorySegment.class)
                    .invoke(null, pVtbl);
            FunctionDescriptor functionDescriptor = methodType.returnType().equals(void.class) ?
                    FunctionDescriptor.ofVoid(
                            methodType.parameterList().stream()
                                    .map(DX12::getCType)
                                    .collect(Collectors.toList())
                                    .toArray(new ValueLayout[]{}))
                    : FunctionDescriptor.of(
                    getCType(methodType.returnType()),
                    methodType.parameterList().stream()
                            .map(DX12::getCType)
                            .collect(Collectors.toList())
                            .toArray(new ValueLayout[]{}));
            MethodHandle methodHandle = getInstance().downcallHandle(methodAddr, methodType, functionDescriptor);
            MemorySegment ppOut = null;
            if (pOutType != null) {
                ppOut = (MemorySegment) pOutType.getMethod("allocatePointer", NativeScope.class).invoke(null, scope);
            }
            // This is super gross but can't think of a better way at the moment...
            if (inArgs.length == 0) {
                if (ppOut != null) {
                    checkResult(methodName, (int) methodHandle.invokeExact(pThis.address(), ppOut.address()));
                } else {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address()));
                }
            } else if (inArgs.length == 1) {
                if (ppOut != null) {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], ppOut.address()));
                } else {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0]));
                }
            } else if (inArgs.length == 2) {
                if (ppOut != null) {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], ppOut.address()));
                } else {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1]));
                }
            } else if (inArgs.length == 3) {
                if (ppOut != null) {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], inArgs[2], ppOut.address()));
                } else {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], inArgs[2]));
                }
            } else if (inArgs.length == 4) {
                if (ppOut != null) {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], inArgs[2], inArgs[3], ppOut.address()));
                } else {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], inArgs[2], inArgs[3]));
                }
            } else if (inArgs.length == 5) {
                if (ppOut != null) {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], inArgs[2], inArgs[3], inArgs[4], ppOut.address()));
                } else {
                    checkResult(methodName, (int) methodHandle.invoke(pThis.address(), inArgs[0], inArgs[1], inArgs[2], inArgs[3], inArgs[4]));
                }
            } else {
                throw new IllegalArgumentException("Cannot handle arguments of length \"" + inArgs.length + "\".");
            }
            return new VTableMethodResult(ppThis, pThis, pVtbl, ppOut);
        } catch (Throwable ex) {
            ex.printStackTrace();
            throw new IllegalArgumentException(ex);
        }
    }

    private static final Map<Class<?>, ValueLayout> TYPES = Map.of(int.class, C_INT, MemoryAddress.class, C_POINTER);
    private static <T> ValueLayout getCType(Class<T> clazz) {
        if (!TYPES.containsKey(clazz)) {
            throw new IllegalArgumentException("Cannot get CTYPE of class \"" + clazz + "\".");
        }
        return TYPES.get(clazz);
    }

    public static MemorySegment asSegment(MemoryAddress addr, MemoryLayout layout) {
        return addr.asSegmentRestricted(layout.byteSize());
    }

    private static final int S_OK = 0x00000000;
    private static final int DXGI_ERROR_INVALID_CALL = (int) -2005270527L;

    private static void checkResult(String function, int result) {
        switch (result) {
            case S_OK ->
                    System.out.println("Function \"" + function + "\" returned successfully!");
            case DXGI_ERROR_INVALID_CALL ->
                    System.out.println("Function \"" + function + "\" returned result: DXGI_ERROR_INVALID_CALL");
            default ->
                    System.out.println("Function \"" + function + "\" returned unknown result: " + Integer.toHexString(result));
        }
    }

    public enum IID {
        // https://github.com/terrafx/terrafx.interop.windows/blob/2d5817519219dc963dbe08baa67997c2821befc4/sources/Interop/Windows/um/d3d12/Windows.cs#L180
        IID_ID3D12Debug(0x344488b7, 0x6846, 0x474b, 0xb9, 0x89, 0xf0, 0x27, 0x44, 0x82, 0x45, 0xe0),
        IID_ID3D12Resource(0x696442be, 0xa72e, 0x4059, 0xbc, 0x79, 0x5b, 0x5c, 0x98, 0x04, 0x0f, 0xad),
        IID_IDXGIAdapter1(0x29038f61, 0x3839, 0x4626, 0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05),
        IID_IDXGIAdapter4(0x3c8d99d1, 0x4fbf, 0x4181, 0xa8, 0x2c, 0xaf, 0x66, 0xbf, 0x7b, 0xd2, 0x4e),
        IID_IDXGIFactory1(0x770aae78, 0xf26f, 0x4dba, 0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87),
        IID_IDXGIFactory5(0x7632e1f5, 0xee65, 0x4dca, 0x87, 0xfd, 0x84, 0xcd, 0x75, 0xf8, 0x83, 0x8d),
        IID_IDXGIFactory7(0xa4966eed, 0x76db, 0x44da, 0x84, 0xc1, 0xee, 0x9a, 0x7a, 0xfb, 0x20, 0xa8),
        IID_ID3D12Device(0x189819f1, 0x1db6, 0x4b57, 0xbe, 0x54, 0x18, 0x21, 0x33, 0x9b, 0x85, 0xf7),
        IID_ID3D12Device5(0x8b4f173b, 0x2fea, 0x4b80, 0x8f, 0x58, 0x43, 0x07, 0x19, 0x1a, 0xb9, 0x5d),
        IID_ID3D12Device8(0x9218e6bb, 0xf944, 0x4f7e, 0xa7, 0x5c, 0xb1, 0xb2, 0xc7, 0xb7, 0x01, 0xf3),
        IID_ID3D12DescriptorHeap(0x8efb471d, 0x616c, 0x4f49, 0x90, 0xf7, 0x12, 0x7b, 0xb7, 0x63, 0xfa, 0x51),
        IID_ID3D12CommandQueue(0x0ec870a6, 0x5d7e, 0x4c22, 0x8c, 0xfc, 0x5b, 0xaa, 0xe0, 0x76, 0x16, 0xed);

        private final MemorySegment guid;

        IID(int data1, int data2, int data3, int... data4) {
            MemoryLayout GUID = MemoryLayout.ofStruct(
                    C_INT.withName("Data1"),
                    C_SHORT.withName("Data2"),
                    C_SHORT.withName("Data3"),
                    MemoryLayout.ofSequence(8, C_CHAR).withName("Data4")
            ).withName("_GUID");
            VarHandle data1Handle = GUID.varHandle(int.class, groupElement("Data1"));
            VarHandle data2Handle = GUID.varHandle(short.class, groupElement("Data2"));
            VarHandle data3Handle = GUID.varHandle(short.class, groupElement("Data3"));
            VarHandle data4Handle = GUID.varHandle(byte.class, groupElement("Data4"),
                    MemoryLayout.PathElement.sequenceElement());
            MemorySegment segment = MemorySegment.allocateNative(GUID);
            data1Handle.set(segment, data1);
            data2Handle.set(segment, (short) data2);
            data3Handle.set(segment, (short) data3);
            IntStream.range(0, 8).forEachOrdered(i -> data4Handle.set(segment, i, (byte) data4[i]));
            this.guid = segment;
        }
    }

    static final MemoryLayout tagWNDCLASSEXW$struct$LAYOUT_ = MemoryLayout.ofStruct(
            C_INT.withName("cbSize"),
            C_INT.withName("style"),
            C_POINTER.withName("lpfnWndProc"),
            C_INT.withName("cbClsExtra"),
            C_INT.withName("cbWndExtra"),
            C_POINTER.withName("hInstance"),
            C_POINTER.withName("hIcon"),
            C_POINTER.withName("hCursor"),
            C_POINTER.withName("hbrBackground"),
            C_POINTER.withName("lpszMenuName"),
            C_POINTER.withName("lpszClassName"),
            C_POINTER.withName("hIconSm")
    ).withName("tagWNDCLASSEXW");

    static MemoryLayout tagWNDCLASSEXW$struct$LAYOUT() {
        return tagWNDCLASSEXW$struct$LAYOUT_;
    }

    public static class tagWNDCLASSEXW {
        static final VarHandle tagWNDCLASSEXW$cbSize$VH_ = tagWNDCLASSEXW$struct$LAYOUT_.varHandle(int.class, MemoryLayout.PathElement.groupElement("cbSize"));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$cbSize$VH() {
            return tagWNDCLASSEXW$cbSize$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$style$VH_ = tagWNDCLASSEXW$struct$LAYOUT_.varHandle(int.class, MemoryLayout.PathElement.groupElement("style"));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$style$VH() {
            return tagWNDCLASSEXW$style$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$lpfnWndProc$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("lpfnWndProc")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$lpfnWndProc$VH() {
            return tagWNDCLASSEXW$lpfnWndProc$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$cbClsExtra$VH_ = tagWNDCLASSEXW$struct$LAYOUT_.varHandle(int.class, MemoryLayout.PathElement.groupElement("cbClsExtra"));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$cbClsExtra$VH() {
            return tagWNDCLASSEXW$cbClsExtra$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$cbWndExtra$VH_ = tagWNDCLASSEXW$struct$LAYOUT_.varHandle(int.class, MemoryLayout.PathElement.groupElement("cbWndExtra"));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$cbWndExtra$VH() {
            return tagWNDCLASSEXW$cbWndExtra$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$hInstance$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("hInstance")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$hInstance$VH() {
            return tagWNDCLASSEXW$hInstance$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$hIcon$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("hIcon")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$hIcon$VH() {
            return tagWNDCLASSEXW$hIcon$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$hCursor$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("hCursor")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$hCursor$VH() {
            return tagWNDCLASSEXW$hCursor$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$hbrBackground$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("hbrBackground")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$hbrBackground$VH() {
            return tagWNDCLASSEXW$hbrBackground$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$lpszMenuName$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("lpszMenuName")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$lpszMenuName$VH() {
            return tagWNDCLASSEXW$lpszMenuName$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$lpszClassName$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("lpszClassName")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$lpszClassName$VH() {
            return tagWNDCLASSEXW$lpszClassName$VH_;
        }

        static final VarHandle tagWNDCLASSEXW$hIconSm$VH_ = MemoryHandles.asAddressVarHandle(tagWNDCLASSEXW$struct$LAYOUT_.varHandle(long.class, MemoryLayout.PathElement.groupElement("hIconSm")));

        static final java.lang.invoke.VarHandle tagWNDCLASSEXW$hIconSm$VH() {
            return tagWNDCLASSEXW$hIconSm$VH_;
        }


        private tagWNDCLASSEXW() {
        }

        public static MemoryLayout $LAYOUT() {
            return tagWNDCLASSEXW$struct$LAYOUT();
        }

        public static VarHandle cbSize$VH() {
            return tagWNDCLASSEXW$cbSize$VH();
        }

        public static @C("UINT") int cbSize$get(@C("struct tagWNDCLASSEXW") MemorySegment seg) {
            return (int) tagWNDCLASSEXW$cbSize$VH().get(seg);
        }

        public static @C("UINT") int cbSize$get(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index) {
            return (int) tagWNDCLASSEXW$cbSize$VH().get(seg.asSlice(index * sizeof()));
        }

        public static void cbSize$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("UINT") int x) {
            tagWNDCLASSEXW$cbSize$VH().set(seg, x);
        }

        public static void cbSize$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("UINT") int x) {
            tagWNDCLASSEXW$cbSize$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle style$VH() {
            return tagWNDCLASSEXW$style$VH();
        }

        public static void style$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("UINT") int x) {
            tagWNDCLASSEXW$style$VH().set(seg, x);
        }

        public static void style$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("UINT") int x) {
            tagWNDCLASSEXW$style$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle lpfnWndProc$VH() {
            return tagWNDCLASSEXW$lpfnWndProc$VH();
        }

        public static void lpfnWndProc$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("WNDPROC") MemoryAddress x) {
            tagWNDCLASSEXW$lpfnWndProc$VH().set(seg, x);
        }

        public static void lpfnWndProc$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("WNDPROC") MemoryAddress x) {
            tagWNDCLASSEXW$lpfnWndProc$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle cbClsExtra$VH() {
            return tagWNDCLASSEXW$cbClsExtra$VH();
        }

        public static void cbClsExtra$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("int") int x) {
            tagWNDCLASSEXW$cbClsExtra$VH().set(seg, x);
        }

        public static void cbClsExtra$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("int") int x) {
            tagWNDCLASSEXW$cbClsExtra$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle cbWndExtra$VH() {
            return tagWNDCLASSEXW$cbWndExtra$VH();
        }

        public static void cbWndExtra$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("int") int x) {
            tagWNDCLASSEXW$cbWndExtra$VH().set(seg, x);
        }

        public static void cbWndExtra$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("int") int x) {
            tagWNDCLASSEXW$cbWndExtra$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle hInstance$VH() {
            return tagWNDCLASSEXW$hInstance$VH();
        }

        public static void hInstance$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("HINSTANCE") MemoryAddress x) {
            tagWNDCLASSEXW$hInstance$VH().set(seg, x);
        }

        public static void hInstance$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("HINSTANCE") MemoryAddress x) {
            tagWNDCLASSEXW$hInstance$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle hIcon$VH() {
            return tagWNDCLASSEXW$hIcon$VH();
        }

        public static void hIcon$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("HICON") MemoryAddress x) {
            tagWNDCLASSEXW$hIcon$VH().set(seg, x);
        }

        public static void hIcon$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("HICON") MemoryAddress x) {
            tagWNDCLASSEXW$hIcon$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle hCursor$VH() {
            return tagWNDCLASSEXW$hCursor$VH();
        }

        public static void hCursor$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("HCURSOR") MemoryAddress x) {
            tagWNDCLASSEXW$hCursor$VH().set(seg, x);
        }

        public static void hCursor$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("HCURSOR") MemoryAddress x) {
            tagWNDCLASSEXW$hCursor$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle hbrBackground$VH() {
            return tagWNDCLASSEXW$hbrBackground$VH();
        }

        public static void hbrBackground$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("HBRUSH") MemoryAddress x) {
            tagWNDCLASSEXW$hbrBackground$VH().set(seg, x);
        }

        public static void hbrBackground$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("HBRUSH") MemoryAddress x) {
            tagWNDCLASSEXW$hbrBackground$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle lpszMenuName$VH() {
            return tagWNDCLASSEXW$lpszMenuName$VH();
        }

        public static void lpszMenuName$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("LPCWSTR") MemoryAddress x) {
            tagWNDCLASSEXW$lpszMenuName$VH().set(seg, x);
        }

        public static void lpszMenuName$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("LPCWSTR") MemoryAddress x) {
            tagWNDCLASSEXW$lpszMenuName$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle lpszClassName$VH() {
            return tagWNDCLASSEXW$lpszClassName$VH();
        }

        public static void lpszClassName$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("LPCWSTR") MemoryAddress x) {
            tagWNDCLASSEXW$lpszClassName$VH().set(seg, x);
        }

        public static void lpszClassName$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("LPCWSTR") MemoryAddress x) {
            tagWNDCLASSEXW$lpszClassName$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static VarHandle hIconSm$VH() {
            return tagWNDCLASSEXW$hIconSm$VH();
        }

        public static void hIconSm$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, @C("HICON") MemoryAddress x) {
            tagWNDCLASSEXW$hIconSm$VH().set(seg, x);
        }

        public static void hIconSm$set(@C("struct tagWNDCLASSEXW") MemorySegment seg, long index, @C("HICON") MemoryAddress x) {
            tagWNDCLASSEXW$hIconSm$VH().set(seg.asSlice(index * sizeof()), x);
        }

        public static long sizeof() {
            return $LAYOUT().byteSize();
        }

        public static @C("struct tagWNDCLASSEXW") MemorySegment allocate() {
            return MemorySegment.allocateNative($LAYOUT());
        }

        public static @C("struct tagWNDCLASSEXW") MemorySegment allocate(NativeScope scope) {
            return scope.allocate($LAYOUT());
        }

        public static @C("struct tagWNDCLASSEXW[]") MemorySegment allocateArray(int len) {
            return MemorySegment.allocateNative(MemoryLayout.ofSequence(len, $LAYOUT()));
        }

        public static @C("struct tagWNDCLASSEXW[]") MemorySegment allocateArray(int len, NativeScope scope) {
            return scope.allocate(MemoryLayout.ofSequence(len, $LAYOUT()));
        }

        public static @C("struct tagWNDCLASSEXW*") MemorySegment allocatePointer() {
            return MemorySegment.allocateNative(C_POINTER);
        }

        public static @C("struct tagWNDCLASSEXW*") MemorySegment allocatePointer(NativeScope scope) {
            return scope.allocate(C_POINTER);
        }

        public static @C("struct tagWNDCLASSEXW") MemorySegment ofAddressRestricted(MemoryAddress addr) {
            return RuntimeHelper.asArrayRestricted(addr, $LAYOUT(), 1);
        }
    }

    public static @C("struct tagWNDCLASSEXW") class WNDCLASSEXW extends tagWNDCLASSEXW {
        private WNDCLASSEXW() {
        }
    }

    public static @C("int") int CS_VREDRAW() {
        return (int) 1L;
    }

    public static @C("int") int CS_HREDRAW() {
        return (int) 2L;
    }

    static final FunctionDescriptor WindowProc = FunctionDescriptor.of(C_LONGLONG,
            C_POINTER,
            C_INT,
            C_LONGLONG,
            C_LONGLONG
    );
    static final FunctionDescriptor LoadCursorW$FUNC_ = FunctionDescriptor.of(C_POINTER,
            C_POINTER,
            C_POINTER
    );
    static final LibraryLookup[] LIBRARIES = RuntimeHelper.libraries(new String[]{});

    static final MethodHandle LoadCursorW$MH_ = RuntimeHelper.downcallHandle(
            LIBRARIES, "LoadCursorW",
            "(Ljdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;)Ljdk/incubator/foreign/MemoryAddress;",
            LoadCursorW$FUNC_, false
    );

    public static @C("HCURSOR") MemoryAddress LoadCursorW(@C("HINSTANCE") Addressable hInstance, @C("LPCWSTR") Addressable lpCursorName) {
        try {
            return (MemoryAddress) LoadCursorW$MH_.invokeExact(hInstance.address(), lpCursorName.address());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final MemoryAddress IDC_ARROW$ADDR_CONSTANT_ = MemoryAddress.ofLong(32512L);

    static MemoryAddress IDC_ARROW() {
        return IDC_ARROW$ADDR_CONSTANT_;
    }

    static final FunctionDescriptor RegisterClassExW$FUNC_ = FunctionDescriptor.of(C_SHORT,
            C_POINTER
    );

    static final MethodHandle RegisterClassExW$MH_ = RuntimeHelper.downcallHandle(
            LIBRARIES, "RegisterClassExW",
            "(Ljdk/incubator/foreign/MemoryAddress;)S",
            RegisterClassExW$FUNC_, false
    );

    static MethodHandle RegisterClassExW$MH() {
        return RegisterClassExW$MH_;
    }

    public static @C("ATOM") short RegisterClassExW(@C("const WNDCLASSEXW*") Addressable x0) {
        try {
            return (short) RegisterClassExW$MH().invokeExact(x0.address());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final FunctionDescriptor CreateWindowExW$FUNC_ = FunctionDescriptor.of(C_POINTER,
            C_LONG,
            C_POINTER,
            C_POINTER,
            C_LONG,
            C_INT,
            C_INT,
            C_INT,
            C_INT,
            C_POINTER,
            C_POINTER,
            C_POINTER,
            C_POINTER
    );

    static final MethodHandle CreateWindowExW$MH_ = RuntimeHelper.downcallHandle(
            LIBRARIES, "CreateWindowExW",
            "(ILjdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;" +
                    "IIIIILjdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;" +
                    "Ljdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;" +
                    ")Ljdk/incubator/foreign/MemoryAddress;",
            CreateWindowExW$FUNC_, false
    );

    public static  MethodHandle CreateWindowExW$MH() {
        return CreateWindowExW$MH_;
    }

    public static @C("HWND") MemoryAddress CreateWindowExW (@C("DWORD") int dwExStyle,
                                                            @C("LPCWSTR") Addressable lpClassName,
                                                            @C("LPCWSTR") Addressable lpWindowName,
                                                            @C("DWORD") int dwStyle, @C("int") int X, @C("int") int Y,
                                                            @C("int") int nWidth, @C("int") int nHeight,
                                                            @C("HWND") Addressable hWndParent,
                                                            @C("HMENU") Addressable hMenu,
                                                            @C("HINSTANCE") Addressable hInstance,
                                                            @C("LPVOID") Addressable lpParam) {
        try {
            return (MemoryAddress) CreateWindowExW$MH().invokeExact(dwExStyle, lpClassName.address(),
                    lpWindowName.address(), dwStyle, X, Y, nWidth, nHeight, hWndParent.address(), hMenu.address(),
                    hInstance.address(), lpParam.address());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final int WS_OVERLAPPEDWINDOW() { return (int)13565952L; }
    static final int CW_USEDEFAULT() { return (int)-2147483648L; }
    static final FunctionDescriptor GetLastError$FUNC_ = FunctionDescriptor.of(C_LONG);
    public static @C("DWORD") int GetLastError () {
        try {
            return (int) GetLastError$MH().invokeExact();
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }
    static final MethodHandle GetLastError$MH_ = RuntimeHelper.downcallHandle(
            LIBRARIES, "GetLastError",
            "()I",
            GetLastError$FUNC_, false
    );
    static final MethodHandle GetLastError$MH() { return GetLastError$MH_; }
    static final FunctionDescriptor ShowWindow$FUNC_ = FunctionDescriptor.of(C_INT,
            C_POINTER,
            C_INT
    );
    static final jdk.incubator.foreign.FunctionDescriptor ShowWindow$FUNC() { return ShowWindow$FUNC_; }

    static final MethodHandle ShowWindow$MH_ = RuntimeHelper.downcallHandle(
            LIBRARIES, "ShowWindow",
            "(Ljdk/incubator/foreign/MemoryAddress;I)I",
            ShowWindow$FUNC_, false
    );
    static final MethodHandle ShowWindow$MH() { return ShowWindow$MH_; }
    public static @C("BOOL") int ShowWindow (@C("HWND") Addressable hWnd, @C("int") int nCmdShow) {
        try {
            return (int)ShowWindow$MH().invokeExact(hWnd.address(), nCmdShow);
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }
    static final int SW_SHOW() { return (int)5L; }
    static final FunctionDescriptor UpdateWindow$FUNC_ = FunctionDescriptor.of(C_INT,
            C_POINTER
    );
    static final FunctionDescriptor UpdateWindow$FUNC() { return UpdateWindow$FUNC_; }

    static final MethodHandle UpdateWindow$MH_ = RuntimeHelper.downcallHandle(
            LIBRARIES, "UpdateWindow",
            "(Ljdk/incubator/foreign/MemoryAddress;)I",
            UpdateWindow$FUNC_, false
    );
    static final java.lang.invoke.MethodHandle UpdateWindow$MH() { return UpdateWindow$MH_; }
    public static @C("BOOL") int UpdateWindow (@C("HWND") Addressable hWnd) {
        try {
            return (int)UpdateWindow$MH().invokeExact(hWnd.address());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    static final int DXGI_FORMAT_R8G8B8A8_UNORM() { return (int)28L; }
    static final int DXGI_FORMAT_B8G8R8A8_UNORM() { return (int)87L; }

    static final int DXGI_USAGE_RENDER_TARGET_OUTPUT() { return (int)32L; }
    static final int DXGI_SWAP_EFFECT_SEQUENTIAL() { return (int)1L; }
    static final int DXGI_SWAP_EFFECT_FLIP_DISCARD() { return (int)4L; }
    static final int DXGI_ALPHA_MODE_UNSPECIFIED() { return (int)0L; }
    static final int DXGI_SCALING_STRETCH() { return (int)0L; }
    static final int DXGI_SCALING_NONE() { return (int)1L; }
    static final int DXGI_SCALING_ASPECT_RATIO_STRETCH() { return (int)2L; }

    static final int D3D_FEATURE_LEVEL_11_0() { return (int)45056L; }
    static final int D3D_FEATURE_LEVEL_11_1() { return (int)45312L; }
    static final int D3D_FEATURE_LEVEL_12_0() { return (int)49152L; }
    static final int D3D_FEATURE_LEVEL_12_1() { return (int)49408L; }
    static final int DXGI_CREATE_FACTORY_DEBUG() { return (int)1L; }
    static final int D3D12_DESCRIPTOR_HEAP_TYPE_RTV() { return (int)2L; }
    static final int D3D12_DESCRIPTOR_HEAP_FLAG_NONE() { return (int)0L; }

}