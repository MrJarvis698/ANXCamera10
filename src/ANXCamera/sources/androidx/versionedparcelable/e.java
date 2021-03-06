package androidx.versionedparcelable;

import android.os.Parcelable;
import android.support.annotation.RestrictTo;
import java.io.InputStream;
import java.io.OutputStream;

@RestrictTo({RestrictTo.Scope.LIBRARY_GROUP})
/* compiled from: ParcelUtils */
public class e {
    private e() {
    }

    public static Parcelable a(i iVar) {
        return new C0172ParcelImpl(iVar);
    }

    public static <T extends i> T a(Parcelable parcelable) {
        if (parcelable instanceof C0172ParcelImpl) {
            return ((C0172ParcelImpl) parcelable).Ea();
        }
        throw new IllegalArgumentException("Invalid parcel");
    }

    public static void a(i iVar, OutputStream outputStream) {
        h hVar = new h((InputStream) null, outputStream);
        hVar.b(iVar);
        hVar.Fa();
    }

    public static <T extends i> T d(InputStream inputStream) {
        return new h(inputStream, (OutputStream) null).Oa();
    }
}
