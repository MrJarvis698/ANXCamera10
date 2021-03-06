package com.bumptech.glide.load.model.a;

import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.text.TextUtils;
import com.bumptech.glide.load.c;
import com.bumptech.glide.load.g;
import com.bumptech.glide.load.model.ModelCache;
import com.bumptech.glide.load.model.l;
import com.bumptech.glide.load.model.n;
import com.bumptech.glide.load.model.t;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/* compiled from: BaseGlideUrlLoader */
public abstract class a<Model> implements t<Model, InputStream> {
    private final t<l, InputStream> xi;
    @Nullable
    private final ModelCache<Model, l> yi;

    protected a(t<l, InputStream> tVar) {
        this(tVar, (ModelCache) null);
    }

    protected a(t<l, InputStream> tVar, @Nullable ModelCache<Model, l> modelCache) {
        this.xi = tVar;
        this.yi = modelCache;
    }

    private static List<c> c(Collection<String> collection) {
        ArrayList arrayList = new ArrayList(collection.size());
        for (String lVar : collection) {
            arrayList.add(new l(lVar));
        }
        return arrayList;
    }

    @Nullable
    public t.a<InputStream> a(@NonNull Model model, int i, int i2, @NonNull g gVar) {
        ModelCache<Model, l> modelCache = this.yi;
        l b2 = modelCache != null ? modelCache.b(model, i, i2) : null;
        if (b2 == null) {
            String e2 = e(model, i, i2, gVar);
            if (TextUtils.isEmpty(e2)) {
                return null;
            }
            l lVar = new l(e2, d(model, i, i2, gVar));
            ModelCache<Model, l> modelCache2 = this.yi;
            if (modelCache2 != null) {
                modelCache2.a(model, i, i2, lVar);
            }
            b2 = lVar;
        }
        List<String> c2 = c(model, i, i2, gVar);
        t.a<InputStream> a2 = this.xi.a(b2, i, i2, gVar);
        return (a2 == null || c2.isEmpty()) ? a2 : new t.a<>(a2.Ce, c(c2), a2.li);
    }

    /* access modifiers changed from: protected */
    public List<String> c(Model model, int i, int i2, g gVar) {
        return Collections.emptyList();
    }

    /* access modifiers changed from: protected */
    @Nullable
    public n d(Model model, int i, int i2, g gVar) {
        return n.DEFAULT;
    }

    /* access modifiers changed from: protected */
    public abstract String e(Model model, int i, int i2, g gVar);
}
