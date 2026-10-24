package jui.ffi;

import java.lang.foreign.FunctionDescriptor;
import java.lang.invoke.MethodType;

import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeForeignAccess;
import org.graalvm.nativeimage.hosted.RuntimeReflection;
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess;

/**
 * Tells native-image what the app reaches only at run time: every native
 * signature in {@link Sigs}, the upcall targets of {@link ObjCClass}, and the
 * shaders. Only native-image loads this class (--features).
 */
public final class NativeImageFeature implements Feature {

    @Override
    public void duringSetup(DuringSetupAccess access) {
        for (FunctionDescriptor fd : Sigs.DOWNCALLS) {
            RuntimeForeignAccess.registerForDowncall(fd);
        }
        for (FunctionDescriptor fd : Sigs.UPCALLS) {
            RuntimeForeignAccess.registerForUpcall(fd);
        }
        // Direct upcalls call their static target without a method handle in between.
        try {
            for (int i = 0; i < ObjCClass.TARGET_NAMES.length; i++) {
                RuntimeForeignAccess.registerForDirectUpcall(ObjCClass.target(i), ObjCClass.TARGET_SIGS[i]);
            }
            RuntimeForeignAccess.registerForDirectUpcall(Blocks.target(), Sigs.IMP_V);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // ObjCClass looks its upcall targets up by name.
        for (int i = 0; i < ObjCClass.TARGET_NAMES.length; i++) {
            MethodType type = ObjCClass.TARGET_SIGS[i].toMethodType();
            try {
                RuntimeReflection.register(
                        ObjCClass.class.getDeclaredMethod(ObjCClass.TARGET_NAMES[i], type.parameterArray()));
            } catch (NoSuchMethodException e) {
                throw new IllegalStateException(e);
            }
        }
        try {
            RuntimeReflection.register(Blocks.class.getDeclaredMethod("invoke1", long.class, long.class));
        } catch (NoSuchMethodException e) {
            throw new IllegalStateException(e);
        }
        Module module = NativeImageFeature.class.getModule();
        RuntimeResourceAccess.addResource(module, "jui/shaders/shader.metallib");
        RuntimeResourceAccess.addResource(module, "jui/shaders/shader.metal");
    }
}
