package io.github.npauloj.mibosmart.data.local

/**
 * The shape the cached device rows were written with, and the kill switch of the cache (ADR-006).
 *
 * A cache read compares this number with the one stored beside the rows; a mismatch empties the table
 * and the next load refetches. Without that rule, the first slice to widen the row shape — D-02 adds
 * the origin filter and pagination — would either crash on a column an old file does not have or
 * serve rows of the wrong shape, and the failure would only show on devices that had used the app
 * before.
 *
 * **Bump it in the same commit as any change to `Device.sq`.** Discarding a cache costs one request;
 * a wrong cache costs a bug report from the one user who had the old file.
 *
 * Version 2 was `productId` (D-03, `2.sqm`); version 3 is `parentProductId`, the hub's own product id
 * as the partner puts it on the sub-device's row (D-04, `3.sqm`). Both migrations backfill the part
 * with the absent value — `''` and NULL — and an absent part is precisely what must never reach a
 * `LockAddress`, so the rows that predate a column are dropped here rather than served: the
 * difference between refetching a list and commanding the wrong device.
 */
internal const val DEVICE_CACHE_SCHEMA_VERSION = 3
