package com.khayom1.missminutes.permissions

/** Returns only permissions that are not already granted, preserving request order. */
internal fun permissionsToRequest(
    requiredPermissions: List<String>,
    isGranted: (String) -> Boolean
): Array<String> {
    val uniquePermissions = requiredPermissions.distinct()
    require(uniquePermissions.all { it.isNotBlank() })
    return uniquePermissions.filterNot(isGranted).toTypedArray()
}
