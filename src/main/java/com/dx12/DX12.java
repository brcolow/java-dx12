package com.dx12;

import com.dx12.d3d12_h;
import com.dx12.dxgi_h;
import com.dx12.dxgi_h$constants$0;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.NativeScope;
import jdk.incubator.foreign.MemoryHandles;
import jdk.incubator.foreign.SequenceLayout;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.invoke.VarHandle;

import static jdk.incubator.foreign.CSupport.*;
import static jdk.incubator.foreign.MemoryLayout.PathElement.groupElement;
/**
* https://cr.openjdk.java.net/~mcimadamore/panama/ffi.html#appendix-full-source-code
*/
public class DX12 {
    public static void main(String[] args) {
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        LibraryLookup dxgi = LibraryLookup.ofLibrary("dxgi");
        // Can replace this with dxgi_h$constants$0.IID_IDXGIFactory$LAYOUT() but not sure how set in stone that syntax is.
        MemoryLayout IID_IDXGIFactory_GUID = MemoryLayout.ofStruct(
            C_INT.withName("Data1"),
            C_SHORT.withName("Data2"),
            C_SHORT.withName("Data3"),
            MemoryLayout.ofSequence(8, C_BOOL).withName("Data4")
        ).withName("_GUID");
        VarHandle data1 = IID_IDXGIFactory_GUID.varHandle(int.class, groupElement("Data1"));
        VarHandle data2 = IID_IDXGIFactory_GUID.varHandle(short.class, groupElement("Data2"));
        VarHandle data3 = IID_IDXGIFactory_GUID.varHandle(short.class, groupElement("Data3"));
        VarHandle data4 = IID_IDXGIFactory_GUID.varHandle(byte.class, groupElement("Data4"), MemoryLayout.PathElement.sequenceElement());
        MemorySegment segment = MemorySegment.allocateNative(
                IID_IDXGIFactory_GUID.map(l -> ((SequenceLayout)l).withElementCount(8), MemoryLayout.PathElement.groupElement("Data4")));
        // Need to set this as: 0x770aae78, 0xf26f, 0x4dba, 0xa8, 0x29, 0x25, 0x3c, 0x83, 0xd1, 0xb3, 0x87 (Thanks, dxgi-rs!)
        data1.set(segment, 0x770aae78);
        data2.set(segment, (short) 0xf26f);
        data3.set(segment, (short) 0x4dba);
        data4.set(segment, 0, (byte) 0xa8);
        data4.set(segment, 1, (byte) 0x29);
        data4.set(segment, 2, (byte) 0x25);
        data4.set(segment, 3, (byte) 0x3c);
        data4.set(segment, 4, (byte) 0x83);
        data4.set(segment, 5, (byte) 0xd1);
        data4.set(segment, 6, (byte) 0xb3);
        data4.set(segment, 7, (byte) 0x87);
        try (var scope = NativeScope.unboundedScope()) {
            var factory = scope.allocate(C_POINTER);
            // https://docs.microsoft.com/en-us/windows/win32/api/dxgi/nf-dxgi-createdxgifactory1
            int hresult = dxgi_h.CreateDXGIFactory1(segment, factory);
            System.out.println("hresult: " + hresult);
            System.out.println("factory: " + factory);
            // https://docs.microsoft.com/en-us/windows/win32/api/guiddef/ns-guiddef-guid
            // https://github.com/bryal/dxgi-rs/blob/d319030ad095cda42e3d46ac3eb9ebddd6e73b48/src/constants.rs#L77
        }
    }
}