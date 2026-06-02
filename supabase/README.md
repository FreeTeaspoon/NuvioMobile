# NuvioMobile Supabase Starter

This folder contains a fork-owned Supabase backend starter for the mobile app.

## Setup

1. Create a new Supabase project.
2. Enable Email provider in Authentication.
3. Run `supabase/migrations/0001_nuvio_mobile_sync.sql` in the SQL editor, or deploy it with the Supabase CLI.
4. Deploy `supabase/functions/delete-account`.
5. Add these values to the repo root `local.properties`:

```properties
SUPABASE_URL=https://your-project.supabase.co
SUPABASE_ANON_KEY=your-anon-key
```

## Notes

- The migration is a compatibility starter, not a byte-for-byte clone of upstream's private backend.
- RLS is enabled for all user-owned tables and uses `auth.uid()`.
- Profile PIN support is functional but simple. `clear_profile_pin_with_account_password` does not verify the account password because Supabase Auth password verification is not available directly from SQL.
- `avatar_catalog` is empty by default. The app can still use custom avatar URLs or color initials.
