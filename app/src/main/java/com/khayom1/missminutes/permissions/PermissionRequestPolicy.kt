package com.khayom1.missminutes.permissions

/** Returns only permissions that are not already granted, preserving request order. */
internal fun permissionsToRequest(
    requiredPermissions: List<String>,
    isGranted: (String) -> Boolean
): Array<String> {
    val requested = linkedSetOf<String>()
    requiredPermissions.forEach { permission ->
        require(permission.isNotBlank())
        if (requested.add(permission) && !isGranted(permission)) {
            // The permission is added only once; already-granted entries are removed below.
        }
    }
    return requiredPermissions.distinct().filterNot(isGranted).toTypedArray()
}
