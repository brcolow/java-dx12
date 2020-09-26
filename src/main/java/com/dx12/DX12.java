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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import static com.dx12.d3d12_h.D3D12CreateDevice;
import static com.dx12.d3d12_h.D3D12_COMMAND_LIST_TYPE_DIRECT;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_DESC;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_FLAG_NONE;
import static com.dx12.d3d12_h.ID3D12CommandQueue;
import static com.dx12.d3d12_h.ID3D12Device;
import static com.dx12.d3d12_h.ID3D12DeviceVtbl;
import static com.dx12.dxgi_h.DXGI_ADAPTER_DESC1;
import static com.dx12.dxgi_h.DXGI_MODE_DESC;
import static com.dx12.dxgi_h.DXGI_SAMPLE_DESC;
import static com.dx12.dxgi_h.DXGI_SWAP_CHAIN_DESC;
import static com.dx12.dxgi_h.IDXGIAdapter1;
import static com.dx12.dxgi_h.IDXGIAdapter1Vtbl;
import static com.dx12.dxgi_h.IDXGIFactory1;
import static com.dx12.dxgi_h.IDXGIFactory1Vtbl;
import static com.dx12.dxgi_h.IDXGISwapChain;

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
        return hwndMain;
    }

    public static void main(String[] args) throws Throwable {
        // TODO: Create a utility to check what Windows version we are running on, and then what Windows versions
        // introduced which version of the various class like ID3D12Device1, 2, 3, etc.
        LibraryLookup user32 = LibraryLookup.ofLibrary("user32");
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        LibraryLookup dxgi = LibraryLookup.ofLibrary("dxgi");
        try (NativeScope scope = NativeScope.unboundedScope()) {
            MemoryAddress hwndMain = createWindow(scope);
            // IDXGIFactory1** dxgiFactory;
            var ppDxgiFactory = IDXGIFactory1.allocatePointer(scope);
            // HRESULT = CreateDXGIFactory1(_uuid(dxgiFactory), &dxgiFactory))
            checkResult(dxgi_h.CreateDXGIFactory1(IID.IID_IDXGIFactory1.guid, ppDxgiFactory));
            // IDXGIFactory1*
            MemorySegment pDxgiFactory = asSegment(MemoryAccess.getAddress(ppDxgiFactory), IDXGIFactory1.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment dxgiFactoryVtbl = asSegment(IDXGIFactory1.lpVtbl$get(pDxgiFactory), IDXGIFactory1Vtbl.$LAYOUT());
            // lpVtbl->EnumAdapters1
            MemoryAddress enumAdaptersAddr = IDXGIFactory1Vtbl.EnumAdapters1$get(dxgiFactoryVtbl);

            // link the pointer
            MethodHandle EnumAdapters1MethodHandle = getInstance().downcallHandle(
                    enumAdaptersAddr,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER));

            /* [annotation][out] _COM_Outptr_  IDXGIAdapter1** */
            MemorySegment ppOut = IDXGIAdapter1.allocatePointer(scope);
            checkResult((int) EnumAdapters1MethodHandle.invokeExact(pDxgiFactory.address(), 0, ppOut.address()));
            // IDXGIAdapter1*
            MemorySegment pAdapter = asSegment(MemoryAccess.getAddress(ppOut), IDXGIAdapter1.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment dxgiAdapterVtbl = asSegment(IDXGIAdapter1.lpVtbl$get(pAdapter), IDXGIAdapter1Vtbl.$LAYOUT());
            // lpVtbl->EnumAdapters1
            // HRESULT(*)(IDXGIAdapter1*,DXGI_ADAPTER_DESC1*)
            MemoryAddress getDesc1Addr = IDXGIAdapter1Vtbl.GetDesc1$get(dxgiAdapterVtbl);

            // link the pointer
            MethodHandle getDesc1MethodHandle = getInstance().downcallHandle(
                    getDesc1Addr,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

            /* DXGI_ADAPTER_DESC1* */
            MemorySegment pDesc = DXGI_ADAPTER_DESC1.allocate(scope);
            checkResult((int) getDesc1MethodHandle.invokeExact(pAdapter.address(), pDesc.address()));

            // print description
            MemorySegment descStr = DXGI_ADAPTER_DESC1.Description$slice(pDesc);
            String str = new String(descStr.toByteArray(), StandardCharsets.UTF_16LE);
            System.out.println(str);

            // ID3D12Device** d3d12Device;
            var ppDevice = ID3D12Device.allocatePointer(scope);

            // D3D12CreateDevice(pAdapter, D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&ppDevice))
            checkResult(D3D12CreateDevice(pAdapter, (int) 45056L, IID.IID_ID3D12Device.guid, ppDevice));
            // ID3D12Device*
            MemorySegment pDevice = asSegment(MemoryAccess.getAddress(ppDevice), ID3D12Device.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment deviceVtbl = asSegment(ID3D12Device.lpVtbl$get(pDevice), ID3D12DeviceVtbl.$LAYOUT());

            // lpVtbl->CreateCommandQueue
            MemoryAddress createCommandQueueAddr = ID3D12DeviceVtbl.CreateCommandQueue$get(deviceVtbl);

            // D3D12_COMMAND_QUEUE_DESC queueDesc = {};
            // queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
            // queueDesc.Flags = D3D12_COMMAND_QUEUE_FLAG_NONE;
            MemorySegment pQueueDesc = D3D12_COMMAND_QUEUE_DESC.allocate(scope);
            // This is unnecessary:
            // You already have a fully readable/writable segment for the struct
            // you should only use `asSegment` if you get a MemoryAddress from the library, but you want to turn it into
            // a memory segment with certain known size (so that you can read and write from it).
            //MemorySegment queueDesc = asSegment(pQueueDesc.address(), D3D12_COMMAND_QUEUE_DESC.$LAYOUT());
            D3D12_COMMAND_QUEUE_DESC.Type$set(pQueueDesc, D3D12_COMMAND_LIST_TYPE_DIRECT());
            D3D12_COMMAND_QUEUE_DESC.Flags$set(pQueueDesc, D3D12_COMMAND_QUEUE_FLAG_NONE());

            // link the pointer (first MemoryAddress argument is the (this) pointer (C++ => C))
            MethodHandle MH_ID3D12Device_CreateCommandQueue = getInstance().downcallHandle(
                    createCommandQueueAddr,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));

            // ID3D12CommandQueue**
            var ppQueue = ID3D12CommandQueue.allocatePointer(scope);

            checkResult((int) MH_ID3D12Device_CreateCommandQueue.invokeExact(pDevice.address(), pQueueDesc.address(),
                    IID.IID_ID3D12CommandQueue.guid.address(), ppQueue.address()));

            /*
            DXGI_SWAP_CHAIN_DESC result = { };
            result.BufferDesc.Width = x;
            result.BufferDesc.Height = y;
            result.BufferDesc.Format = format;
            result.SampleDesc.Count = 1;
            result.SampleDesc.Quality = 0;
            result.BufferUsage = usage;
            result.BufferCount = bufferCount;
            result.OutputWindow = hWnd;
            result.Windowed = true;
            result.SwapEffect = swapEffect;
            result.Flags = 0;

            dxgiFactory->CreateSwapChain(..)
             */
            // FIXME: We want to use: CreateSwapChainForHwnd not CreateSwapChain!
            // https://docs.microsoft.com/en-us/windows/win32/api/dxgi1_2/nf-dxgi1_2-idxgifactory2-createswapchainforhwnd
            MemorySegment pSwapChainDesc = DXGI_SWAP_CHAIN_DESC.allocate(scope);
            //MemorySegment pBufferDesc = DXGI_MODE_DESC.allocate(scope);
            //DXGI_MODE_DESC.Width$set(pBufferDesc, 0);
            //DXGI_MODE_DESC.Height$set(pBufferDesc, 0);
            //DXGI_MODE_DESC.Format$set(pBufferDesc, DXGI_FORMAT_R8G8B8A8_UNORM());
            DXGI_SWAP_CHAIN_DESC.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("BufferDesc"), MemoryLayout.PathElement.groupElement("Width")).set(pSwapChainDesc, 0);
            DXGI_SWAP_CHAIN_DESC.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("BufferDesc"), MemoryLayout.PathElement.groupElement("Height")).set(pSwapChainDesc, 0);
            DXGI_SWAP_CHAIN_DESC.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("BufferDesc"), MemoryLayout.PathElement.groupElement("Format")).set(pSwapChainDesc, DXGI_FORMAT_R8G8B8A8_UNORM());

            //DXGI_SWAP_CHAIN_DESC.BufferDesc$slice(pSwapChainDesc).
            //System.out.println("buffer desc segment: " + DXGI_MODE_DESC.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("Width")));
            //MemorySegment pSampleDesc = DXGI_SAMPLE_DESC.allocate(scope);
            //DXGI_SAMPLE_DESC.Count$set(pSampleDesc, 1); // The number of multisamples per pixel.
            //DXGI_SAMPLE_DESC.Quality$set(pSampleDesc, 0); // The image quality level.
            DXGI_SWAP_CHAIN_DESC.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("SampleDesc"), MemoryLayout.PathElement.groupElement("Count")).set(pSwapChainDesc, 1);
            DXGI_SWAP_CHAIN_DESC.$LAYOUT().varHandle(int.class, MemoryLayout.PathElement.groupElement("SampleDesc"), MemoryLayout.PathElement.groupElement("Quality")).set(pSwapChainDesc, 0);
            // This is out of bounds...
            //DXGI_SWAP_CHAIN_DESC.SampleDesc$slice(pSwapChainDesc);
            DXGI_SWAP_CHAIN_DESC.BufferUsage$set(pSwapChainDesc, DXGI_USAGE_RENDER_TARGET_OUTPUT());
            DXGI_SWAP_CHAIN_DESC.BufferCount$set(pSwapChainDesc, 1);
            DXGI_SWAP_CHAIN_DESC.OutputWindow$set(pSwapChainDesc, hwndMain.address());
            DXGI_SWAP_CHAIN_DESC.Windowed$set(pSwapChainDesc, 0);
            DXGI_SWAP_CHAIN_DESC.SwapEffect$set(pSwapChainDesc, DXGI_SWAP_EFFECT_SEQUENTIAL());
            DXGI_SWAP_CHAIN_DESC.Flags$set(pSwapChainDesc, 0);
            MemoryAddress addrCreateSwapChain = IDXGIFactory1Vtbl.CreateSwapChain$get(dxgiFactoryVtbl);
            MethodHandle createSwapChainMethodHandle = getInstance().downcallHandle(
                    addrCreateSwapChain,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER));
            MemorySegment ppSwapChain = IDXGISwapChain.allocatePointer(scope);
            int result = (int) createSwapChainMethodHandle.invokeExact(pDxgiFactory.address(), pDevice.address(), ppSwapChain.address());

            System.out.println("CreateSwapChain result: " + String.format("%X8", result));
            System.in.read();
        }
    }

    public static MemorySegment asSegment(MemoryAddress addr, MemoryLayout layout) {
        return addr.asSegmentRestricted(layout.byteSize());
    }

    private static final int S_OK = 0x00000000;

    private static void checkResult(int result) {
        switch (result) {
            case S_OK -> {
            }
            default -> throw new IllegalStateException("Unknown result: " + String.format("%X8", result));
        }
    }

    public enum IID {
        // https://github.com/terrafx/terrafx.interop.windows/blob/2d5817519219dc963dbe08baa67997c2821befc4/sources/Interop/Windows/um/d3d12/Windows.cs#L180
        IID_IDXGIAdapter1(0x29038f61, 0x3839, 0x4626, 0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05),
        IID_IDXGIAdapter4(0x3c8d99d1, 0x4fbf, 0x4181, 0xa8, 0x2c, 0xaf, 0x66, 0xbf, 0x7b, 0xd2, 0x4e),
        IID_IDXGIFactory1(0x770aae78, 0xf26f, 0x4dba, 0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87),
        IID_IDXGIFactory7(0xa4966eed, 0x76db, 0x44da, 0x84, 0xc1, 0xee, 0x9a, 0x7a, 0xfb, 0x20, 0xa8),
        IID_ID3D12Device(0x189819f1, 0x1db6, 0x4b57, 0xbe, 0x54, 0x18, 0x21, 0x33, 0x9b, 0x85, 0xf7),
        IID_ID3D12Device5(0x8b4f173b, 0x2fea, 0x4b80, 0x8f, 0x58, 0x43, 0x07, 0x19, 0x1a, 0xb9, 0x5d),
        IID_ID3D12Device8(0x9218e6bb, 0xf944, 0x4f7e, 0xa7, 0x5c, 0xb1, 0xb2, 0xc7, 0xb7, 0x01, 0xf3),
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

    static final int DXGI_FORMAT_R8G8B8A8_UNORM() { return (int)28L; }
    static final int DXGI_USAGE_RENDER_TARGET_OUTPUT() { return (int)32L; }
    static final int DXGI_SWAP_EFFECT_SEQUENTIAL() { return (int)1L; }

}