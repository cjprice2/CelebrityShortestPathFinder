import "./globals.css";

export const metadata = {
  title: "Celebrity Shortest Path Finder",
  description: "Find the shortest connection between celebrities through their shared titles using bidirectional BFS!",
  icons: {
    icon: './favicon.ico?v=2',
  },
};

export default function RootLayout({ children }) {
  return (
    <html lang="en">
      <body className="antialiased">
        {children}
      </body>
    </html>
  );
}
