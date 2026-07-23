/**
 * Encryption of secrets at rest, published as an interface of {@code shared} because any module
 * that stores a credential needs it and none of them should carry its own cipher.
 *
 * <p>Named explicitly rather than left internal: a second implementation of secret storage is
 * exactly the outcome this package exists to prevent.
 */
@org.springframework.modulith.NamedInterface("secret")
package com.orgmemory.core.shared.secret;
