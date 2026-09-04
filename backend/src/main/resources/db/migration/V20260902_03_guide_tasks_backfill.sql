-- Backfill the demo guides into the independent structured task field.
-- The body is kept as narrative content; task text is no longer parsed at runtime.
UPDATE guide
SET tasks = JSON_ARRAY(
        'Define your wedding vibe & venue type',
        'Set your dress budget',
        'Start a moodboard',
        'Book a Color Palette consultation'
    ),
    tasks_count = 4,
    body = 'Set your vision, budget, and date.'
WHERE title = 'Dream & Discover'
  AND (tasks IS NULL OR tasks = '' OR tasks = '[]');

UPDATE guide
SET tasks = JSON_ARRAY(
        'Browse silhouettes by venue',
        'Order fabric swatches',
        'Try styles at home',
        'Place your gown order (allow custom time)'
    ),
    tasks_count = 4,
    body = 'The fun part — finding the one.'
WHERE title = 'Find Your Gown'
  AND (tasks IS NULL OR tasks = '' OR tasks = '[]');

UPDATE guide
SET tasks = JSON_ARRAY(
        'Choose your bridesmaid palette',
        'Share the group link with your party',
        'Order mother-of-the-bride dress',
        'Select flower girl looks'
    ),
    tasks_count = 4,
    body = 'Dress your bridesmaids and family.'
WHERE title = 'Style Your Party'
  AND (tasks IS NULL OR tasks = '' OR tasks = '[]');

UPDATE guide
SET tasks = JSON_ARRAY(
        'Choose your veil & headpiece',
        'Pick wedding shoes',
        'Add jewelry & finishing touches',
        'Plan a second reception look'
    ),
    tasks_count = 4,
    body = 'Complete every look.'
WHERE title = 'Accessorize'
  AND (tasks IS NULL OR tasks = '' OR tasks = '[]');

UPDATE guide
SET tasks = JSON_ARRAY(
        'Schedule alterations',
        'Break in your shoes',
        'Final accessory check',
        'Confirm delivery dates'
    ),
    tasks_count = 4,
    body = 'Perfect the fit.'
WHERE title = 'Final Fittings'
  AND (tasks IS NULL OR tasks = '' OR tasks = '[]');

-- Additional acceptance data for the complete timeline.
INSERT INTO guide (phase, timeframe, title, tasks_count, tasks, status, body)
SELECT 'Phase 6', '2-4 weeks out', 'Wedding Week Ready', 5,
       JSON_ARRAY(
           'Confirm final measurements and alterations',
           'Steam and pack the gown safely',
           'Prepare an emergency sewing kit',
           'Share the getting-ready timeline with your party',
           'Confirm the venue delivery contact'
       ), 2,
       'Bring every detail together so the final week feels calm and organized.'
WHERE NOT EXISTS (SELECT 1 FROM guide WHERE title = 'Wedding Week Ready');

INSERT INTO guide (phase, timeframe, title, tasks_count, tasks, status, body)
SELECT 'Phase 7', 'After the celebration', 'Preserve the Memories', 4,
       JSON_ARRAY(
           'Arrange professional gown cleaning',
           'Choose a preservation box or display case',
           'Save your veil and accessories together',
           'Record the vendors and details you loved'
       ), 1,
       'A few thoughtful steps will help preserve the look and memories for years to come.'
WHERE NOT EXISTS (SELECT 1 FROM guide WHERE title = 'Preserve the Memories');
