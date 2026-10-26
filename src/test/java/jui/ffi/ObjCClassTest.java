package jui.ffi;

import jui.testing.Check;

public final class ObjCClassTest {
    public void testMethodsWithRangesRectsAndPointsRoundTrip() {
        java.util.List<String> failures = jui.spike.FfiCheck.check();
        Check.isTrue(failures.isEmpty(), failures.toString());
    }

    public void testAClassTakesAProtocol() {
        long cls = ObjCClass.define("JUIProtocolTest", "NSObject").protocol("NSTextInputClient").register();
        long obj = ObjC.send(ObjC.send(cls, "alloc"), "init");
        try {
            Check.isTrue(ObjC.sendBool(obj, "conformsToProtocol:", ObjC.protocol("NSTextInputClient")), "conforms");
        } finally {
            ObjC.release(obj);
        }
    }

    public void testAnUnknownProtocolFails() {
        Check.fails(IllegalStateException.class,
                () -> ObjCClass.define("JUIProtocolTest2", "NSObject").protocol("NoSuchProtocol"));
    }
}
