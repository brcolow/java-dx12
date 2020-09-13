package com.dx12;

import com.dx12.d3d12_h;
import com.dx12.dxgi_h;
import com.dx12.dxgi_h$constants$0;
import jdk.incubator.foreign.FunctionDescriptor;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.NativeScope;
import jdk.incubator.foreign.MemoryAccess;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.SequenceLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;
import java.util.Map;

import static com.dx12.dxgi_h.IDXGIFactory1Vtbl.EnumAdapters1$VH;
import static com.dx12.dxgi_h.IDXGIFactory1Vtbl.EnumAdapters1$get;
import static com.dx12.dxgi_h.IDXGIFactory1Vtbl.EnumAdapters1$set;
import static jdk.incubator.foreign.CSupport.*;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;
/**
* https://cr.openjdk.java.net/~mcimadamore/panama/ffi.html#appendix-full-source-code
*/
public class DX12 {

    public enum IID {
        IID_IDXGIAdapter1,
        IID_IDXGIFactory1
    }

    public static MemorySegment GUID(IID iid) {
        return GUID_MAP.get(iid);
    }

    private static final Map<IID, MemorySegment> GUID_MAP = Map.of(
            IID.IID_IDXGIFactory1,
            //   0x770aae78, 0xf26f, 0x4dba,           0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87
            GUID(0x770aae78, 0xf26f, 0x4dba, new int[]{0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87}),
            IID.IID_IDXGIAdapter1,
            //   0x29038f61, 0x3839, 0x4626,           0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05
            GUID(0x29038f61, 0x3839, 0x4626, new int[]{0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05}));

    public static MemorySegment GUID(int data1, int data2, int data3, int[] data4) {
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
        MemorySegment segment = MemorySegment.allocateNative(GUID.map(l -> ((SequenceLayout) l).withElementCount(8),
                MemoryLayout.PathElement.groupElement("Data4")));
        data1Handle.set(segment, data1);
        data2Handle.set(segment, (short) data2);
        data3Handle.set(segment, (short) data3);
        for (int i = 0; i < 8; i++) {
            data4Handle.set(segment, i, (byte) data4[i]);
        }
        return segment;
    }

    // https://docs.microsoft.com/en-us/windows/win32/direct3d12/creating-a-basic-direct3d-12-component
    public static void main(String[] args) {
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        LibraryLookup dxgi = LibraryLookup.ofLibrary("dxgi");
        try (var scope = NativeScope.unboundedScope()) {
            // IDXGIFactory1** dxgiFactory;
            var dxgiFactory = scope.allocate(C_POINTER);
            // https://docs.microsoft.com/en-us/windows/win32/api/dxgi/nf-dxgi-createdxgifactory1
            // HRESULT = CreateDXGIFactory1(_uuid(dxgiFactory), &dxgiFactory))
            int hresult = dxgi_h.CreateDXGIFactory1(GUID(IID.IID_IDXGIFactory1), dxgiFactory);
            System.out.println("hresult: " + hresult);
            var dxgiAdapter = scope.allocate(C_POINTER);
            System.out.println("IDXGIFactory1Vtbl byte size: " + dxgi_h.IDXGIFactory1Vtbl.$LAYOUT().byteSize()); // 112
            MemorySegment segment = MemorySegment.allocateNative(dxgi_h.IDXGIFactory1Vtbl.$LAYOUT().byteSize());
            MemoryAddress address = segment.address().addOffset(64);
            FunctionDescriptor functionDescriptor = FunctionDescriptor.of(C_INT, // Return type = HRESULT
                    C_POINTER,
                    C_INT, // arg0 = UINT
                    C_POINTER // arg1 = IDXGIAdapter1 **ppAdapter
            );
            MethodType methodType = MethodType.methodType(int.class, MemoryAddress.class, int.class, MemoryAddress.class);
            MethodHandle methodHandle = getSystemLinker().downcallHandle(address, methodType, functionDescriptor);
            try {
                hresult = (int) methodHandle.invokeExact(dxgiFactory.address(), 0, dxgiAdapter.address());
            } catch (Throwable ex) {
                ex.printStackTrace();
            }
            System.out.println("vh: " + EnumAdapters1$VH());
            //EnumAdapters1$get(dxgiFactory, MemoryAccess.getAddress(dxgiAdapter));
            // Now we need to implement and call the following:
            /*
            void GetHardwareAdapter(IDXGIFactory4* pFactory, IDXGIAdapter1** ppAdapter)
            {
                *ppAdapter = nullptr;
                for (UINT adapterIndex = 0; ; ++adapterIndex)
                {
                    IDXGIAdapter1* pAdapter = nullptr;
                    if (DXGI_ERROR_NOT_FOUND == dxgiFactory->EnumAdapters1(adapterIndex, &pAdapter))
                    {
                        // No more adapters to enumerate.
                        break;
                    }

                    // Check to see if the adapter supports Direct3D 12, but don't create the
                    // actual device yet.
                    if (SUCCEEDED(D3D12CreateDevice(pAdapter, D3D_FEATURE_LEVEL_11_0, _uuidof(ID3D12Device), nullptr)))
                    {
                        *ppAdapter = pAdapter;
                        return;
                    }
                    pAdapter->Release();
                }
            }
             */
            //for (int adapterIndex = 0; ; adapterIndex++) {
                // WRONG: dxgiFactory.EnumAdapters1(adapterIndex, dxgiAdapter);
                // dxgiFactory->EnumAdapters1(adapterIndex, &dxgiAdapter)
            //}
            //getSystemLinker().downcallHandle(dxgi_h.IDXGIFactory1Vtbl.EnumAdapters1$VH()
            // ComPtr<IDXGIAdapter1> hardwareAdapter;
            // define_guid!(IID_IDXGIAdapter1,0x29038f61, 0x3839, 0x4626, 0x91, 0xfd, 0x08, 0x68, 0x79, 0x01, 0x1a, 0x05);
            // GetHardwareAdapter(factory.Get(), &hardwareAdapter);
            // D3D12CreateDevice(hardwareAdapter.Get(),D3D_FEATURE_LEVEL_11_0,IID_PPV_ARGS(&m_device))
            // https://docs.microsoft.com/en-us/windows/win32/api/guiddef/ns-guiddef-guid
            // https://github.com/bryal/dxgi-rs/blob/d319030ad095cda42e3d46ac3eb9ebddd6e73b48/src/constants.rs#L77
        }
    }
}