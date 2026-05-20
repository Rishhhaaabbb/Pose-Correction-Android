Configuring SMS OTP via Supabase (using Twilio)

Overview
- This project uses Supabase REST auth endpoints for phone OTP.
- To deliver real SMS OTPs you must configure an SMS gateway (e.g. Twilio) in your Supabase project.

Steps to enable SMS OTP (Supabase Cloud)
1. Create a Twilio account (https://www.twilio.com/) and purchase a phone number that can send SMS.
   - Get Account SID and Auth Token, and optionally Messaging Service SID.

2. In Supabase (Project > Settings > Auth > SMS / Provider)
   - Choose Twilio as the SMS provider and enter the Twilio credentials (Account SID, Auth Token, From number or Messaging Service SID).
   - Enable phone sign-in (if there is a toggle / policy for phone auth).

3. Update your local.properties in the project root (do NOT commit real secrets):
   - Copy `local.properties.example` to `local.properties` and fill the values:
     SUPABASE_URL_DEBUG=https://<your-project>.supabase.co
     SUPABASE_ANON_KEY_DEBUG=<your-anon-key>
     TWILIO_ACCOUNT_SID=AC...    # optional for reference
     TWILIO_AUTH_TOKEN=...       # optional for reference
     TWILIO_PHONE_NUMBER=+1...   # optional for reference

4. Ensure your app points to the correct Supabase URL and uses the anon key (already wired via BuildConfig).
   - If running Supabase locally on the emulator, use http://10.0.2.2:8000 and ensure network security allows cleartext for debug (already added).

5. Test the flow
   - Run the app (debug build) on a device/emulator with network access.
   - Open the Phone OTP screen, enter username and a real phone number (E.164 format or 10-digit local will be normalized to +91 by the app).
   - Tap Generate OTP. Supabase should request OTP from Twilio and SMS should be delivered to the phone.
   - Enter the received OTP and continue — user profile will be created.

Troubleshooting
- If you don't receive SMS, check Supabase logs (Project > Settings > Logs / Auth > Logs) to see request/response for /auth/v1/otp.
- Check Twilio logs to confirm messages were accepted by Twilio and delivered.
- Common errors:
  - 401/403: wrong anon key or project URL
  - Network/timeout: device cannot reach Supabase
  - Twilio errors: invalid From number, messaging service misconfiguration, account balance/permissions

Security note
- Never commit `local.properties` with real secrets to source control.
- For production, use environment secrets on CI/CD and server-side configuration.

If you'd like, I can:
- Walk through Supabase dashboard steps and validate the expected logs with you.
- Patch a sample `local.properties` (example file added) or create a one-off test function to exercise the OTP endpoints and show sample responses.
