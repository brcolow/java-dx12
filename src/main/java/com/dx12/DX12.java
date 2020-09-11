package com.dx12;

import com.dx12.d3d12_h;
import jdk.incubator.foreign.MemoryAddress;
import jdk.incubator.foreign.MemoryLayout;
import jdk.incubator.foreign.MemorySegment;
import jdk.incubator.foreign.LibraryLookup;
import jdk.incubator.foreign.NativeScope;

/**
* https://cr.openjdk.java.net/~mcimadamore/panama/ffi.html#appendix-full-source-code
*/
public class DX12 {
    public static void main(String[] args) {
        LibraryLookup d3d12 = LibraryLookup.ofLibrary("D3D12");
        // Probably need to load dxgi.dll as well.
        try (var scope = NativeScope.unboundedScope()) {
            var factory = scope.allocate(C_POINTER);
            // https://docs.microsoft.com/en-us/windows/win32/api/dxgi/nf-dxgi-createdxgifactory1
            // Now we need to call dxgi_h.CreateDXGIFactory1(UUID(factory), factory)
            // The somewhat tricky part is being able to get the UUID.
            // https://docs.microsoft.com/en-us/windows/win32/api/guiddef/ns-guiddef-guid
            // https://github.com/bryal/dxgi-rs/blob/d319030ad095cda42e3d46ac3eb9ebddd6e73b48/src/constants.rs#L77
        }
    }
}