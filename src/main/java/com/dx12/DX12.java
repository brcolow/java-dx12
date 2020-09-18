package com.dx12;

import jdk.incubator.foreign.Addressable;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.NativeScope;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

/*
import static com.dx12.d3d12_h.D3D12CreateDevice;
import static com.dx12.d3d12_h.D3D12_COMMAND_LIST_TYPE_DIRECT;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_DESC;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_FLAG_NONE;
import static com.dx12.d3d12_h.ID3D12CommandQueue;
import static com.dx12.d3d12_h.ID3D12Device;
import static com.dx12.d3d12_h.ID3D12DeviceVtbl;
import static com.dx12.dxgi_h.DXGI_ADAPTER_DESC1;
import static com.dx12.dxgi_h.IDXGIAdapter1;
import static com.dx12.dxgi_h.IDXGIAdapter1Vtbl;
import static com.dx12.dxgi_h.IDXGIFactory1;
import static com.dx12.dxgi_h.IDXGIFactory1Vtbl;
 */

import static jdk.incubator.foreign.CLinker.*;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;

/**
 * https://github.com/Meith/DX12/tree/master/DX12
 *
 * https://cr.openjdk.java.net/~mcimadamore/panama/ffi.html#appendix-full-source-code
 */
public class DX12 {

    public enum IID {
        IID_IDXGIAdapter1     (0x29038f61, 0x3839, 0x4626, 0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05),
        IID_IDXGIFactory1     (0x770aae78, 0xf26f, 0x4dba, 0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87),
        IID_ID3D12Device      (0x189819f1, 0x1db6, 0x4b57, 0xbe, 0x54, 0x18, 0x21, 0x33, 0x9b, 0x85, 0xf7),
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

    public static void createWindow(NativeScope scope) {
        //MemorySegment pwindowClass = tagWNDCLASSEXW.allocate(scope);

        /*
            // Initialize the window class.
            WNDCLASSEX windowClass = { 0 };
            windowClass.cbSize = sizeof(WNDCLASSEX);
            windowClass.style = CS_HREDRAW | CS_VREDRAW;
            windowClass.lpfnWndProc = WindowProc;
            windowClass.hInstance = hInstance;
            windowClass.hCursor = LoadCursor(NULL, IDC_ARROW);
            windowClass.lpszClassName = L"DXSampleClass";
            RegisterClassEx(&windowClass);

            RECT windowRect = { 0, 0, static_cast<LONG>(pSample->GetWidth()), static_cast<LONG>(pSample->GetHeight()) };
            AdjustWindowRect(&windowRect, WS_OVERLAPPEDWINDOW, FALSE);

            // Create the window and store a handle to it.
            m_hwnd = CreateWindow(
                windowClass.lpszClassName,
                pSample->GetTitle(),
                WS_OVERLAPPEDWINDOW,
                CW_USEDEFAULT,
                CW_USEDEFAULT,
                windowRect.right - windowRect.left,
                windowRect.bottom - windowRect.top,
                nullptr,        // We have no parent window.
                nullptr,        // We aren't using menus.
                hInstance,
                pSample);
         */
    }
    //static final LibraryLookup[] LIBRARIES = RuntimeHelper.libraries(new String[] {});
    public static @C("HRESULT") int CreateDXGIFactory1(@C("const IID*") Addressable riid, @C("void**") Addressable ppFactory) {
        try {
            return (int) RuntimeHelper.downcallHandle(
            new LibraryLookup[] { LibraryLookup.ofDefault() }, "CreateDXGIFactory1",
                    "(Ljdk/incubator/foreign/MemoryAddress;Ljdk/incubator/foreign/MemoryAddress;)I",
                    FunctionDescriptor.of(C_INT,
                            C_POINTER,
                            C_POINTER
                    ), false
            ).invokeExact(riid.address(), ppFactory.address());
        } catch (Throwable ex) {
            throw new AssertionError(ex);
        }
    }

    public static class DXGI_ADAPTER_DESC1 {
        @C("struct DXGI_ADAPTER_DESC1")
        public static MemorySegment allocate(NativeScope scope) {
            return scope.allocate(MemoryLayout.ofStruct(
                    MemoryLayout.ofSequence(128, C_SHORT).withName("Description"),
                    C_INT.withName("VendorId"),
                    C_INT.withName("DeviceId"),
                    C_INT.withName("SubSysId"),
                    C_INT.withName("Revision"),
                    C_LONGLONG.withName("DedicatedVideoMemory"),
                    C_LONGLONG.withName("DedicatedSystemMemory"),
                    C_LONGLONG.withName("SharedSystemMemory"),
                    MemoryLayout.ofStruct(
                            C_INT.withName("LowPart"),
                            C_INT.withName("HighPart")
                    ).withName("AdapterLuid"),
                    C_INT.withName("Flags"),
                    MemoryLayout.ofPaddingBits(32)
            ).withName("DXGI_ADAPTER_DESC1"));
        }
        public static MemorySegment Description$slice(MemorySegment var0) {
            return RuntimeHelper.nonCloseableNonTransferableSegment(var0.asSlice(0L, 256L));
        }
    }
    public static void main(String[] args) throws Throwable {
        LibraryLookup user32 = LibraryLookup.ofLibrary("user32");
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        LibraryLookup dxgi = LibraryLookup.ofLibrary("dxgi");
        try (NativeScope scope = NativeScope.unboundedScope()) {
            createWindow(scope);
            // IDXGIFactory1** dxgiFactory;
            var ppDxgiFactory = scope.allocate(C_POINTER);
            // HRESULT = CreateDXGIFactory1(_uuid(dxgiFactory), &dxgiFactory))
            checkResult(CreateDXGIFactory1(IID.IID_IDXGIFactory1.guid, ppDxgiFactory));
            // IDXGIFactory1*
            MemorySegment pDxgiFactory = asSegment(MemoryAccess.getAddress(ppDxgiFactory), MemoryLayout.ofStruct(
                    C_POINTER.withName("lpVtbl")
            ).withName("IDXGIFactory1"));

            // (This)->lpVtbl
            MemoryLayout vtblLayout = NemoryLayout.ofStruct(
                    C_POINTER.withName("QueryInterface"),
                    C_POINTER.withName("AddRef"),
                    C_POINTER.withName("Release"),
                    C_POINTER.withName("SetPrivateData"),
                    C_POINTER.withName("SetPrivateDataInterface"),
                    C_POINTER.withName("GetPrivateData"),
                    C_POINTER.withName("GetParent"),
                    C_POINTER.withName("EnumAdapters"),
                    C_POINTER.withName("MakeWindowAssociation"),
                    C_POINTER.withName("GetWindowAssociation"),
                    C_POINTER.withName("CreateSwapChain"),
                    C_POINTER.withName("CreateSoftwareAdapter"),
                    C_POINTER.withName("EnumAdapters1"),
                    C_POINTER.withName("IsCurrent")
            ).withName("IDXGIFactory1Vtbl");
            MemorySegment vtbl = asSegment(IDXGIFactory1.lpVtbl$get(pDxgiFactory), vtblLayout);
            // lpVtbl->EnumAdapters1
            MemoryAddress addrEnumAdapters =  MemoryHandles.asAddressVarHandle(vtblLayout.varHandle(long.class, MemoryLayout.PathElement.groupElement("EnumAdapters1"))).get(vtbl);

            // link the pointer
            MethodHandle MH_EnumAdapters1 = getInstance().downcallHandle(
                    addrEnumAdapters,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER));

            /* [annotation][out] _COM_Outptr_  IDXGIAdapter1** */
            MemorySegment ppOut = scope.allocate(C_POINTER);
            checkResult((int) MH_EnumAdapters1.invokeExact(pDxgiFactory.address(), 0, ppOut.address()));
            // IDXGIAdapter1*
            MemorySegment pAdapter = asSegment(MemoryAccess.getAddress(ppOut), MemoryLayout.ofStruct(
                    C_POINTER.withName("lpVtbl")
            ).withName("IDXGIAdapter1"));

            // (This)->lpVtbl
            MemoryLayout adapterVtblLayout = MemoryLayout.ofStruct(
                    C_POINTER.withName("QueryInterface"),
                    C_POINTER.withName("AddRef"),
                    C_POINTER.withName("Release"),
                    C_POINTER.withName("SetPrivateData"),
                    C_POINTER.withName("SetPrivateDataInterface"),
                    C_POINTER.withName("GetPrivateData"),
                    C_POINTER.withName("GetParent"),
                    C_POINTER.withName("EnumOutputs"),
                    C_POINTER.withName("GetDesc"),
                    C_POINTER.withName("CheckInterfaceSupport"),
                    C_POINTER.withName("GetDesc1")
            ).withName("IDXGIAdapter1Vtbl");
            MemorySegment vtbl2 = asSegment(IDXGIAdapter1.lpVtbl$get(pAdapter), adapterVtblLayout);
            // lpVtbl->EnumAdapters1
            // HRESULT(*)(IDXGIAdapter1*,DXGI_ADAPTER_DESC1*)
            MemoryAddress addrGetDesc1 = MemoryHandles.asAddressVarHandle(adapterVtblLayout.varHandle(long.class, MemoryLayout.PathElement.groupElement("GetDesc1"))).get(vtbl);
            //MemoryAddress addrGetDesc1 = IDXGIAdapter1Vtbl.GetDesc1$get(vtbl2);

            // link the pointer
            MethodHandle MH_GetDesc1 = getInstance().downcallHandle(
                    addrGetDesc1,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

            /* DXGI_ADAPTER_DESC1* */
            //MemorySegment pDesc = DXGI_ADAPTER_DESC1.allocate(scope);
            MemorySegment pDesc = scope.allocate(MemoryLayout.ofStruct(
                    MemoryLayout.ofSequence(128, C_SHORT).withName("Description"),
                    C_INT.withName("VendorId"),
                    C_INT.withName("DeviceId"),
                    C_INT.withName("SubSysId"),
                    C_INT.withName("Revision"),
                    C_LONGLONG.withName("DedicatedVideoMemory"),
                    C_LONGLONG.withName("DedicatedSystemMemory"),
                    C_LONGLONG.withName("SharedSystemMemory"),
                    MemoryLayout.ofStruct(
                            C_LONG.withName("LowPart"),
                            C_LONG.withName("HighPart")
                    ).withName("AdapterLuid"),
                    C_INT.withName("Flags"),
                    MemoryLayout.ofPaddingBits(32)
            ).withName("DXGI_ADAPTER_DESC1"));
            checkResult((int) MH_GetDesc1.invokeExact(pAdapter.address(), pDesc.address()));

            // print description
            MemorySegment descStr = RuntimeHelper.nonCloseableNonTransferableSegment(pDesc.asSlice(0, 256));
            //MemorySegment descStr = DXGI_ADAPTER_DESC1.Description$slice(pDesc);
            String str = new String(descStr.toByteArray(), StandardCharsets.UTF_16LE);
            System.out.println(str);

            /*
            // ID3D12Device** d3d12Device;
            var ppDevice = ID3D12Device.allocatePointer(scope);

            // D3D12CreateDevice(pAdapter, D3D_FEATURE_LEVEL_11_0, IID_PPV_ARGS(&ppDevice))
            checkResult(D3D12CreateDevice(pAdapter, (int) 45056L, IID.IID_ID3D12Device.guid, ppDevice));
            // ID3D12Device*
            MemorySegment pDevice = asSegment(MemoryAccess.getAddress(ppDevice), ID3D12Device.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment deviceVtbl = asSegment(ID3D12Device.lpVtbl$get(pDevice), ID3D12DeviceVtbl.$LAYOUT());

            // lpVtbl->CreateCommandQueue
            MemoryAddress addrCreateCommandQueue = ID3D12DeviceVtbl.CreateCommandQueue$get(deviceVtbl);

            // D3D12_COMMAND_QUEUE_DESC queueDesc = {};
            // queueDesc.Type = D3D12_COMMAND_LIST_TYPE_DIRECT;
            // queueDesc.Flags = D3D12_COMMAND_QUEUE_FLAG_NONE;
            MemorySegment pQueueDesc =  D3D12_COMMAND_QUEUE_DESC.allocate(scope);
            // This is unnecessary:
            // You already have a fully readable/writable segment for the struct
            // you should only use `asSegment` if you get a MemoryAddress from the library, but you want to turn it into
            // a memory segment with certain known size (so that you can read and write from it).
            //MemorySegment queueDesc = asSegment(pQueueDesc.address(), D3D12_COMMAND_QUEUE_DESC.$LAYOUT());
            D3D12_COMMAND_QUEUE_DESC.Type$set(pQueueDesc, D3D12_COMMAND_LIST_TYPE_DIRECT());
            D3D12_COMMAND_QUEUE_DESC.Flags$set(pQueueDesc, D3D12_COMMAND_QUEUE_FLAG_NONE());

            // link the pointer (first MemoryAddress argument is the (this) pointer (C++ => C)
            MethodHandle MH_ID3D12Device_CreateCommandQueue = getSystemLinker().downcallHandle(
                    addrCreateCommandQueue,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER, C_POINTER, C_POINTER));

            // ID3D12CommandQueue**
            var ppQueue = ID3D12CommandQueue.allocatePointer(scope);

            checkResult((int) MH_ID3D12Device_CreateCommandQueue.invokeExact(pDevice.address(), pQueueDesc.address(),
                    IID.IID_ID3D12CommandQueue.guid.address(), ppQueue.address()));

             */
        }
    }

    public static MemorySegment asSegment(MemoryAddress addr, MemoryLayout layout) {
        return addr.asSegmentRestricted(layout.byteSize()).withOwnerThread(Thread.currentThread());
    }

    private static final int S_OK = 0x00000000;

    private static void checkResult(int result) {
        switch (result) {
            case S_OK -> {}
            default -> throw new IllegalStateException("Unknown result: " + String.format("%X8", result));
        }
    }
}