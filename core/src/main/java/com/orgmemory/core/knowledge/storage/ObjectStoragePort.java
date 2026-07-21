package com.orgmemory.core.knowledge.storage;

import java.io.InputStream;

public interface ObjectStoragePort {

    StoredObject put(ObjectWriteRequest request, InputStream content);

    ObjectContent open(ObjectKey key);

    StoredObject stat(ObjectKey key);

    void delete(ObjectKey key);
}
