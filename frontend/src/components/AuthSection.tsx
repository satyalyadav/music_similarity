import { Button } from "./ui/button";
import { Avatar, AvatarImage, AvatarFallback } from "./ui/avatar";
import { LogIn, LogOut, Music2 } from "lucide-react";

interface AuthSectionProps {
  isConnected: boolean;
  user: {
    displayName?: string | null;
    spotifyId?: string | null;
    product?: string | null;
    imageUrl?: string | null;
  } | null;
  onConnect: () => void;
  onDisconnect: () => void;
}

function getInitials(displayName: string | null | undefined): string {
  if (!displayName) return "?";
  const parts = displayName.trim().split(/\s+/);
  if (parts.length === 0) return "?";
  if (parts.length === 1) return parts[0].charAt(0).toUpperCase();
  return (parts[0].charAt(0) + parts[parts.length - 1].charAt(0)).toUpperCase();
}

export function AuthSection({ isConnected, user, onConnect, onDisconnect }: AuthSectionProps) {
  return (
    <div className="flex items-center justify-between p-6 bg-white rounded-2xl shadow-sm border border-gray-100">
      <div className="flex items-center gap-4">
        {isConnected && user ? (
          <Avatar className="w-12 h-12 rounded-full shadow-lg ring-2 ring-white overflow-hidden">
            <AvatarImage src={user.imageUrl || undefined} alt={user.displayName || "User"} className="rounded-full" />
            <AvatarFallback 
              className="text-white font-semibold text-sm rounded-full"
              style={{ 
                background: 'linear-gradient(to bottom right, rgb(74, 222, 128), rgb(5, 150, 105))',
                backgroundColor: 'transparent'
              }}
            >
              {getInitials(user.displayName)}
            </AvatarFallback>
          </Avatar>
        ) : (
          <div className="p-3 rounded-xl shadow-lg" style={{ background: 'var(--spotify-green)' }}>
            <Music2 className="w-6 h-6 text-white" />
          </div>
        )}
        <div>
          {isConnected && user ? (
            <>
              <p className="text-gray-900">{user.displayName || "Music Lover"}</p>
              <p className="text-sm text-gray-500">
                {user.spotifyId ? `${user.spotifyId} • ` : ""}
                {user.product 
                  ? user.product.charAt(0).toUpperCase() + user.product.slice(1).toLowerCase()
                  : "Premium"}
              </p>
            </>
          ) : (
            <>
              <p className="text-gray-900">Connect your Spotify</p>
              <p className="text-sm text-gray-500">Required to get personalized recommendations</p>
            </>
          )}
        </div>
      </div>
      
      {isConnected ? (
        <Button
          variant="outline"
          onClick={onDisconnect}
          className="gap-2"
        >
          <LogOut className="w-4 h-4" />
          Disconnect
        </Button>
      ) : (
        <Button
          onClick={onConnect}
          className="gap-2 text-white hover:opacity-90"
          style={{ background: 'var(--spotify-green)' }}
        >
          <LogIn className="w-4 h-4" />
          Connect Spotify
        </Button>
      )}
    </div>
  );
}

