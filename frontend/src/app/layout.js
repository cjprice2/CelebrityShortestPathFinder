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
      <head>
        <meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover, user-scalable=no" />
        {/* Match header/footer bg (gray-900) for iOS safe areas/status bar */}
        <meta name="theme-color" content="#101828" />
        <meta name="mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-capable" content="yes" />
        <meta name="apple-mobile-web-app-status-bar-style" content="black-translucent" />
      </head>
        <body className="antialiased min-h-screen">
        {children}
      </body>
    </html>
  );
}
