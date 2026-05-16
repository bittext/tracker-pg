/** Curated short inspirational lines (public domain / widely attributed) for the welcome page. */
export interface InspirationalQuote {
  readonly text: string;
  readonly author: string;
}

export const INSPIRATIONAL_QUOTES: readonly InspirationalQuote[] = [
  {
    text: 'The happiness of your life depends upon the quality of your thoughts.',
    author: 'Marcus Aurelius',
  },
  {
    text: 'Well done is better than well said.',
    author: 'Benjamin Franklin',
  },
  {
    text: 'It does not matter how slowly you go as long as you do not stop.',
    author: 'Confucius',
  },
  {
    text: 'You have power over your mind - not outside events. Realize this, and you will find strength.',
    author: 'Marcus Aurelius',
  },
  {
    text: 'The only person you are destined to become is the person you decide to be.',
    author: 'Ralph Waldo Emerson',
  },
  {
    text: 'You are never too old to set another goal or to dream a new dream.',
    author: 'C.S. Lewis',
  },
  {
    text: "Believe you can and you're halfway there.",
    author: 'Theodore Roosevelt',
  },
  {
    text: 'Success is not final, failure is not fatal: it is the courage to continue that counts.',
    author: 'Winston Churchill',
  },
  {
    text: 'The future belongs to those who believe in the beauty of their dreams.',
    author: 'Eleanor Roosevelt',
  },
  {
    text: "I have learned over the years that when one's mind is made up, this diminishes fear.",
    author: 'Rosa Parks',
  },
  {
    text: 'The way to get started is to quit talking and begin doing.',
    author: 'Walt Disney',
  },
  {
    text: 'When we are no longer able to change a situation, we are challenged to change ourselves.',
    author: 'Viktor Frankl',
  },
  {
    text: 'Our greatest glory is not in never falling, but in rising every time we fall.',
    author: 'Confucius',
  },
  {
    text: 'Excellence is never an accident.',
    author: 'Aristotle',
  },
  {
    text: 'Do what you can, with what you have, where you are.',
    author: 'Theodore Roosevelt',
  },
  {
    text: 'The sun himself is weak when he first rises, and gathers strength and courage as the day gets on.',
    author: 'Charles Dickens',
  },
  {
    text: 'Nature does not hurry, yet everything is accomplished.',
    author: 'Lao Tzu',
  },
  {
    text: "If you don't like something, change it. If you can't change it, change your attitude.",
    author: 'Maya Angelou',
  },
  {
    text: 'Optimism is the faith that leads to achievement. Nothing can be done without hope and confidence.',
    author: 'Helen Keller',
  },
  {
    text: 'The journey of a thousand miles begins with a single step.',
    author: 'Lao Tzu',
  },
  {
    text: 'He who has a why to live can bear almost any how.',
    author: 'Friedrich Nietzsche',
  },
  {
    text: 'It is never too late to be what you might have been.',
    author: 'George Eliot',
  },
  {
    text: 'The best time to plant a tree was twenty years ago. The second best time is now.',
    author: 'Chinese proverb',
  },
  {
    text: 'Act as if what you do makes a difference. It does.',
    author: 'William James',
  },
];

export function pickRandomQuote(exclude?: InspirationalQuote): InspirationalQuote {
  const list = INSPIRATIONAL_QUOTES;
  if (list.length === 0) {
    return { text: 'Keep going.', author: 'Unknown' };
  }
  for (let i = 0; i < 32; i++) {
    const c = list[Math.floor(Math.random() * list.length)]!;
    if (!exclude || c.text !== exclude.text || c.author !== exclude.author) {
      return c;
    }
  }
  return list[0]!;
}
