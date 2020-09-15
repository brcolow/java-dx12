package com.dx12;

import com.dx12.d3d12_h;
import com.dx12.dxgi_h;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.NativeScope;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.SequenceLayout;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Map;
import java.nio.charset.StandardCharsets;
import java.util.stream.IntStream;

import static com.dx12.dxgi_h.DXGI_ADAPTER_DESC1;
import static com.dx12.dxgi_h.IDXGIAdapter1;
import static com.dx12.dxgi_h.IDXGIAdapter1Vtbl;
import static com.dx12.dxgi_h.IDXGIFactory1;
import static com.dx12.dxgi_h.IDXGIFactory1Vtbl;

import static com.dx12.d3d12_h.D3D12CreateDevice;
import static com.dx12.d3d12_h.ID3D12CommandQueue;
import static com.dx12.d3d12_h.ID3D12Device;
import static com.dx12.d3d12_h.ID3D12DeviceVtbl;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_DESC;
import static com.dx12.d3d12_h.D3D12_COMMAND_LIST_TYPE_DIRECT;
import static com.dx12.d3d12_h.D3D12_COMMAND_QUEUE_FLAG_NONE;

import static jdk.incubator.foreign.CSupport.*;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;

/**
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
                    MemoryLayout.ofSequence(8, C_BOOL).withName("Data4")
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

    public static void main(String[] args) throws Throwable {
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        LibraryLookup dxgi = LibraryLookup.ofLibrary("dxgi");
        try (NativeScope scope = NativeScope.unboundedScope()) {
            // IDXGIFactory1** dxgiFactory;
            var ppDxgiFactory = IDXGIFactory1.allocatePointer(scope);
            // HRESULT = CreateDXGIFactory1(_uuid(dxgiFactory), &dxgiFactory))
            checkResult(dxgi_h.CreateDXGIFactory1(IID.IID_IDXGIFactory1.guid, ppDxgiFactory));
            // IDXGIFactory1*
            MemorySegment pDxgiFactory = asSegment(MemoryAccess.getAddress(ppDxgiFactory), IDXGIFactory1.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment vtbl = asSegment(IDXGIFactory1.lpVtbl$get(pDxgiFactory), IDXGIFactory1Vtbl.$LAYOUT());
            // lpVtbl->EnumAdapters1
            MemoryAddress addrEnumAdapters = IDXGIFactory1Vtbl.EnumAdapters1$get(vtbl);

            // link the pointer
            MethodHandle MH_EnumAdapters1 = getSystemLinker().downcallHandle(
                    addrEnumAdapters,
                    MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_INT, C_POINTER));

            /* [annotation][out] _COM_Outptr_  IDXGIAdapter1** */
            MemorySegment ppOut = IDXGIAdapter1.allocatePointer(scope);
            checkResult((int) MH_EnumAdapters1.invokeExact(pDxgiFactory.address(), 0, ppOut.address()));
            // IDXGIAdapter1*
            MemorySegment pAdapter = asSegment(MemoryAccess.getAddress(ppOut), IDXGIAdapter1.$LAYOUT());

            // (This)->lpVtbl
            MemorySegment vtbl2 = asSegment(IDXGIAdapter1.lpVtbl$get(pAdapter), IDXGIAdapter1Vtbl.$LAYOUT());
            // lpVtbl->EnumAdapters1
            // HRESULT(*)(IDXGIAdapter1*,DXGI_ADAPTER_DESC1*)
            MemoryAddress addrGetDesc1 = IDXGIAdapter1Vtbl.GetDesc1$get(vtbl2);

            // link the pointer
            MethodHandle MH_GetDesc1 = getSystemLinker().downcallHandle(
                    addrGetDesc1,
                    MethodType.methodType(int.class, MemoryAddress.class, MemoryAddress.class),
                    FunctionDescriptor.of(C_INT, C_POINTER, C_POINTER));

            /* DXGI_ADAPTER_DESC1* */
            MemorySegment pDesc = DXGI_ADAPTER_DESC1.allocate(scope);
            checkResult((int) MH_GetDesc1.invokeExact(pAdapter.address(), pDesc.address()));

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
        }
    }

    public static MemorySegment asSegment(MemoryAddress addr, MemoryLayout layout) {
        return MemorySegment.ofNativeRestricted(addr, layout.byteSize(), Thread.currentThread(), null, null);
    }

    private static final int S_OK = 0x00000000;

    private static void checkResult(int result) {
        switch (result) {
            case S_OK -> {}
            default -> throw new IllegalStateException("Unknown result: " + String.format("%X8", result));
        }
    }
}