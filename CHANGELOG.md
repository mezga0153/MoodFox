# Changelog

## v1.2.0 (2026-03-31)

### New Characters
- Added 4 new helper characters: **Cat**, **Dog**, **Frog**, **Panda** — each with 21 mood expressions (−10 to +10)
- Expanded Settings character picker from 2 options (Fox/Emoji) to 6, using a grid layout
- All screens (Check-in, Calendar, Settings) updated to support the new characters

### Improvements
- Added generic `characterDrawableForValue()` mapping function for cleaner character rendering
- Added `helper_dog` and `helper_frog` string resources in English, Slovenian, and Hungarian

### Tooling
- Added `generate-character.js` script for AI-assisted character generation with identity-preservation prompting and automatic image optimization (sips + pngquant)

---

## v1.1.0

- Weather tracking integration (Open-Meteo API)
- Mood cause categories and correlation analysis
- Excel and ZIP backup export
- Theme selection (5 hues × dark/light)
- Slovenian and Hungarian localizations

## v1.0.0

- Initial release
- Mood check-in (−10 to +10 scale)
- Calendar view
- Basic analysis and trend charts
- Fox helper character
- Privacy-first, local-only storage
