package com.noor.shoplocal.data

/**
 * Configuration for the hosted ShopLocal backend.
 *
 * The backend is a Supabase project (Postgres + PostgREST) that exposes a RESTful
 * API over HTTPS:
 *   - GoTrue auth endpoints under `/auth/v1/...`  (register / login, bcrypt-hashed)
 *   - PostgREST CRUD endpoints under `/rest/v1/<table>`
 *   - A custom endpoint `/rest/v1/rpc/place_order` we authored server-side.
 *
 * The [ANON_KEY] is a *public* client key by design — it identifies the project,
 * not a user. Access to data is protected by Row-Level Security on the server, so
 * shipping this key in the app is safe and is the intended Supabase pattern.
 */
object Supabase {
    const val URL = "https://swchtpvdtncslvlsxeul.supabase.co"
    const val ANON_KEY =
        "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJzdXBhYmFzZSIsInJlZiI6InN3Y2h0cHZkdG5jc2x2bHN4ZXVsIiwicm9sZSI6ImFub24iLCJpYXQiOjE3ODQ5MjQ5NjYsImV4cCI6MjEwMDUwMDk2Nn0.wxih6yML-LfgI_aN2tcLtLmxbJKIEtIJL_p303hLxAo"

    const val REST = "$URL/rest/v1"
    const val AUTH = "$URL/auth/v1"
}
