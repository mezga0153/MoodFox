#!/usr/bin/env node
/**
 * generate-character.js
 *
 * Generates 21 mood images (-10 to +10) for a new MoodFox character using a
 * hub-and-spoke approach to minimise character drift:
 *
 *   For every mood step, the ORIGINAL neutral (mood 0) image is sent as the
 *   PRIMARY reference (first image). The previous mood step is sent as a
 *   SECONDARY reference only as an expression-direction hint.
 *
 * This ensures the model always anchors to the original character design,
 * limiting drift to at most one generation away from the source.
 *
 * Requires Node.js 18+ (built-in fetch & FormData).
 *
 * Usage:
 *   node scripts/generate-character.js \
 *     --name cat \
 *     --neutral path/to/cat_neutral.png \
 *     --api-key sk-...                    \   # or set OPENAI_API_KEY env var
 *     [--output app/mobile/src/main/res/drawable-nodpi]
 *     [--start -10]                           # resume: skip already-existing files
 *     [--end 10]
 */

import fs             from 'fs';
import path           from 'path';
import { execSync }   from 'child_process';
import { parseArgs }  from 'util';
import OpenAI         from 'openai';

// ── Mood expressions ─────────────────────────────────────────────────────────
// Each label is used in the prompt for that specific step.

const MOOD_EXPRESSIONS = {
  '-10': 'very sad, big watery eyes with visible tears, clearly downcast and dejected',
  '-9':  'very upset, big teary eyes, a couple of tear drops',
  '-8':  'very sad, tears pooling in eyes, shaky lower lip',
  '-7':  'sad, eyes glistening with moisture, clear frown, visibly unhappy',
  '-6':  'clearly sad, downturned mouth, eyes half-closed and heavy',
  '-5':  'unhappy, frowning, dejected, eyes looking downward',
  '-4':  'mildly unhappy, slight frown, feeling a bit down',
  '-3':  'a bit down, mild frown, slightly blue',
  '-2':  'slightly unhappy, subtle downward expression',
  '-1':  'nearly neutral with a very subtle hint of sadness',
   '0':  'perfectly neutral, calm and composed, neither happy nor sad',
   '1':  'nearly neutral with a very subtle hint of warmth',
   '2':  'slightly happy, hint of a gentle smile',
   '3':  'mildly happy, small warm smile',
   '4':  'happy, friendly warm smile',
   '5':  'quite happy, bright upbeat smile',
   '6':  'very happy, big cheerful smile, eyes bright and wide',
   '7':  'joyful, enthusiastic wide smile, rosy cheeks, eyes sparkling',
   '8':  'very joyful, beaming with happiness, small golden sparkles near the character',
   '9':  'extremely happy, ecstatic, eyes shining, a few small stars and sparkles nearby',
  '10':  'euphoric, huge grin, radiating delight, small stars and sparkles around the character',
};

// ── Helpers ──────────────────────────────────────────────────────────────────

function moodToFilename(name, value) {
  const suffix = value < 0 ? `neg${Math.abs(value)}` : `${value}`;
  return `${name.toLowerCase()}_mood_${suffix}.png`;
}

function moodToLabel(value) {
  return value > 0 ? `+${value}` : `${value}`;
}

/**
 * Build a concise edit prompt for the image edit endpoint.
 * The edit endpoint works best with short, direct instructions.
 */
function buildPrompt(characterName, referenceValue, moodValue) {
  const expression   = MOOD_EXPRESSIONS[String(moodValue)];
  const absVal       = Math.abs(moodValue);
  const scaleContext = moodValue === 0
    ? 'This is the neutral midpoint (0 on a scale of -10 to +10).'
    : `This is mood ${moodToLabel(moodValue)} on a scale of -10 to +10 — ` +
      `${absVal}/10 ${moodValue > 0 ? 'positive' : 'negative'}.`;

  const allowsAtmosphere = moodValue >= 8; // only for positively intense moods, and even then only a few tiny accents
  const atmosphereRule   = allowsAtmosphere
    ? `   - At this intensity you MAY add a few tiny atmospheric accents (e.g. sparkles, small tear drops) floating in the empty space OUTSIDE the character outline. Keep them minimal and do not let them overlap the character.\n`
    : `   - Do NOT add any particles, sparkles, effects, or atmospheric accents of any kind.\n`;

  const hasPrevRef = referenceValue !== moodValue && referenceValue !== 0;
  const prevRefNote = hasPrevRef
    ? `- The SECOND image shows mood ${moodToLabel(referenceValue)} — use it ONLY as a directional hint for the expression. Ignore any shape, colour, or proportion differences it may have from the neutral; the neutral is always the authority.\n`
    : '';

  return (
    `You are a image creator. Below are the RULES you must follow, then the TASK you must perform.\n\n` +

    `═══════════════════════════════════════════════════\n` +
    `RULES\n` +
    `═══════════════════════════════════════════════════\n\n` +

    `IMAGE REFERENCES:\n` +
    `- The FIRST image is the MASTER REFERENCE (neutral mood 0). It defines the character's identity absolutely.\n` +
    (hasPrevRef ? `- ${prevRefNote}` : '') +
    `\n` +

    `WHAT YOU MUST FREEZE — do not alter these under any circumstances:\n` +
    `   - Head shape and silhouette\n` +
    `   - Ear shape, size, position and colour\n` +
    `   - Eye shape and style (e.g. round, almond, dot) — only the expression within that style changes\n` +
    `   - Muzzle / snout shape and size\n` +
    `   - Nose shape and position\n` +
    `   - Outline / line thickness and stroke style\n` +
    `   - Colour palette (every fur colour, skin tone, accent colour, highlight colour)\n` +
    `   - Shading style (flat, cel-shaded, soft — match exactly)\n` +
    `   - Character size, position and framing within the canvas\n` +
    `   - Any accessories or markings present in the neutral (spots, patches, scarves, etc.)\n` +
    `\n` +

    `WHAT YOU MAY CHANGE — expression features only:\n` +
    `   - Eyebrow angle and curvature\n` +
    `   - Eyelid position and degree of openness\n` +
    `   - Pupil shape (e.g. round vs. teary) staying within the eye boundary\n` +
    `   - Mouth shape (smile, frown, open, closed)\n` +
    `   - Cheek blush presence or absence\n` +
    (absVal >= 8 ? `   - Visible tear drops on cheeks (only at high sadness)\n` : '') +
    atmosphereRule +
    `\n` +

    `TRANSPARENCY RULES (MANDATORY):\n` +
    `   a. Everything OUTSIDE the character's outline must be fully transparent (alpha = 0). No white, no colour fill, no scenery.\n` +
    `   b. Everything INSIDE the character's outline must be fully opaque (alpha = 255) — including all eye parts (pupils, irises, sclera, highlights), cheeks, inner ears, and any interior detail.\n` +
    `   c. NEVER make eyes or any eye component semi-transparent. They must be solid and fully painted.\n` +
    `   d. The ONLY transparent pixels in the output are empty space around the character's outline.\n\n` +

    `═══════════════════════════════════════════════════\n` +
    `TASK — THIS IS WHAT YOU MUST ACTUALLY DO\n` +
    `═══════════════════════════════════════════════════\n\n` +

    `Apply the following expression to the ${characterName} character from the MASTER REFERENCE image.\n` +
    `Keep everything else pixel-identical — only change the facial expression.\n\n` +

    `Mood ${moodToLabel(moodValue)}: ${expression}\n` +
    `${scaleContext}`
  );
}

// ── API call (OpenAI SDK — images.edit) ──────────────────────────────────────

async function callImageEdit(client, neutralImagePath, referenceImagePath, prompt) {
  const PNG_MAGIC = Buffer.from([0x89, 0x50, 0x4E, 0x47]);

  // Validate and collect images
  const neutralBuffer = fs.readFileSync(neutralImagePath);
  if (neutralBuffer.length < 100) {
    throw new Error(`Neutral image too small (${neutralBuffer.length} bytes) — file may be corrupt: ${neutralImagePath}`);
  }
  if (!neutralBuffer.subarray(0, 4).equals(PNG_MAGIC)) {
    throw new Error(`Neutral image is not a valid PNG (magic bytes: ${neutralBuffer.subarray(0, 4).toString('hex')}): ${neutralImagePath}`);
  }
  console.log(`       📎 Image 1: ${path.basename(neutralImagePath)} (${neutralBuffer.length} bytes)`);

  const images = [new File([neutralBuffer], path.basename(neutralImagePath), { type: 'image/png' })];

  if (referenceImagePath && referenceImagePath !== neutralImagePath) {
    const refBuffer = fs.readFileSync(referenceImagePath);
    if (refBuffer.length < 100) {
      throw new Error(`Reference image too small (${refBuffer.length} bytes): ${referenceImagePath}`);
    }
    if (!refBuffer.subarray(0, 4).equals(PNG_MAGIC)) {
      throw new Error(`Reference image is not a valid PNG: ${referenceImagePath}`);
    }
    images.push(new File([refBuffer], path.basename(referenceImagePath), { type: 'image/png' }));
    console.log(`       📎 Image 2: ${path.basename(referenceImagePath)} (${refBuffer.length} bytes)`);
  } else {
    console.log(`       📎 Image 2: (none — only neutral uploaded)`);
  }

  const response = await client.images.edit({
    model: 'chatgpt-image-latest',
    image: images,
    prompt,
    quality: 'high',
    output_format: 'png',
    background: 'transparent',
    input_fidelity: 'high',
    n: 1,
    size: '1024x1024',
  });

  const item = response.data?.[0];
  if (item?.b64_json) return Buffer.from(item.b64_json, 'base64');
  if (item?.url) {
    const imgRes = await fetch(item.url);
    return Buffer.from(await imgRes.arrayBuffer());
  }
  throw new Error('No image data in response');
}

// ── PNG optimisation ─────────────────────────────────────────────────────────

/**
 * Resize to max 512 px with sips, then quantise colours with pngquant.
 * Both tools are expected to be on PATH; errors are logged but non-fatal.
 */
function optimizeImage(filePath) {
  try {
    execSync(`sips -Z 512 "${filePath}" --out "${filePath}"`, { stdio: 'pipe' });
  } catch (e) {
    console.warn(`       ⚠ sips failed: ${e.message}`);
  }
  try {
    execSync(`pngquant --quality=65-80 --force --ext .png "${filePath}"`, { stdio: 'pipe' });
  } catch (e) {
    // pngquant exits 98 when quality can't be met — not a real error
    if (!e.message.includes('exit code 98')) {
      console.warn(`       ⚠ pngquant failed: ${e.message}`);
    }
  }
}

// ── Single step ───────────────────────────────────────────────────────────────

/**
 * Generate the image for moodValue using referenceImagePath as input.
 * Returns the output path (which becomes the reference for the next step).
 * If the output already exists, skips the API call and returns the existing path.
 */
async function generateStep(client, characterName, referenceImagePath, referenceValue, moodValue, outputDir, neutralImagePath) {
  const filename   = moodToFilename(characterName, moodValue);
  const outputPath = path.join(outputDir, filename);

  if (fs.existsSync(outputPath)) {
    console.log(`  ⏭  ${moodToLabel(moodValue).padStart(3)}  ${filename} (exists, using as next reference)`);
    return outputPath;
  }

  const prompt = buildPrompt(characterName, referenceValue, moodValue);
  console.log(`  →  ${moodToLabel(moodValue).padStart(3)}  ${filename}`);
  console.log(`\n── Prompt ──────────────────────────────────────\n${prompt}\n────────────────────────────────────────────────\n`);

  // Hub-and-spoke: neutral is always the primary image; previous step is just a hint
  const imageData = await callImageEdit(client, neutralImagePath, referenceImagePath, prompt);
  fs.writeFileSync(outputPath, imageData);
  console.log(`       ✓ saved`);
  optimizeImage(outputPath);
  console.log(`       ✓ optimized`);
  return outputPath;
}

// ── Main ──────────────────────────────────────────────────────────────────────

async function main() {
  const { values } = parseArgs({
    options: {
      name:      { type: 'string' },
      neutral:   { type: 'string' },
      'api-key': { type: 'string' },
      output:    { type: 'string', default: 'app/mobile/src/main/res/drawable-nodpi' },
      start:     { type: 'string', default: '-10' },
      end:       { type: 'string', default: '10' },
    },
  });

  const characterName = values.name;
  const neutralPath   = values.neutral;
  const apiKey        = values['api-key'] || process.env.OPENAI_API_KEY;
  const outputDir     = values.output;
  const startVal      = parseInt(values.start, 10);
  const endVal        = parseInt(values.end,   10);

  if (!characterName || !neutralPath || !apiKey) {
    console.error([
      '',
      'Usage:',
      '  node scripts/generate-character.js \\',
      '    --name <character>     character name (used for filenames)',
      '    --neutral <image.png>  path to the neutral mood-0 reference image',
      '    --api-key <sk-...>     OpenAI API key (or set OPENAI_API_KEY)',
      '    [--output <dir>]       output directory (default: drawable-nodpi)',
      '    [--start -10]          first mood value to generate (for resuming)',
      '    [--end 10]             last mood value to generate',
      '',
    ].join('\n'));
    process.exit(1);
  }

  if (!fs.existsSync(neutralPath)) {
    console.error(`Error: neutral image not found: ${neutralPath}`);
    process.exit(1);
  }

  fs.mkdirSync(outputDir, { recursive: true });

  // Copy neutral image to output dir as mood_0 so it can be used as a reference
  const neutralOutputPath = path.join(outputDir, moodToFilename(characterName, 0));
  if (!fs.existsSync(neutralOutputPath)) {
    fs.copyFileSync(neutralPath, neutralOutputPath);
    console.log(`\nCopied neutral → ${path.basename(neutralOutputPath)}`);
    optimizeImage(neutralOutputPath);
    console.log(`       ✓ optimized`);
  }

  console.log(`\nCharacter : ${characterName}`);
  console.log(`Reference : ${neutralPath}`);
  console.log(`Output    : ${outputDir}`);
  console.log(`Strategy  : hub-and-spoke (neutral is always the primary reference)\n`);

  const client = new OpenAI({ apiKey });

  let successCount = 0;
  let failCount    = 0;

  const delay = () => new Promise(r => setTimeout(r, 2000));

  // ── Negative side: 0 → -1 → -2 → … → -10 ───────────────────────────────
  if (startVal < 0) {
    console.log('── Negative mood chain ─────────────────────────');
    let refPath   = neutralOutputPath;
    let refValue  = 0;

    for (let v = -1; v >= startVal; v--) {
      if (v > 0) continue; // shouldn't happen but guard
      refPath  = await generateStep(client, characterName, refPath, refValue, v, outputDir, neutralOutputPath);
      refValue = v;
      successCount++;
      if (v > startVal) await delay();
    }
  }

  // ── Positive side: 0 → +1 → +2 → … → +10 ───────────────────────────────
  if (endVal > 0) {
    console.log('\n── Positive mood chain ─────────────────────────────');
    let refPath   = neutralOutputPath;
    let refValue  = 0;

    for (let v = 1; v <= endVal; v++) {
      refPath  = await generateStep(client, characterName, refPath, refValue, v, outputDir, neutralOutputPath);
      refValue = v;
      successCount++;
      if (v < endVal) await delay();
    }
  }

  console.log(`\nDone. ${successCount} generated, ${failCount} failed.`);

  if (successCount > 0) {
    console.log('\nAll generated images have been optimized (sips + pngquant).');
  }
}

main().catch(err => {
  console.error(err);
  process.exit(1);
});
