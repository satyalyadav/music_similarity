import { Switch } from "./ui/switch";
import { Label } from "./ui/label";
import { Alert, AlertDescription } from "./ui/alert";
import { Headphones, AlertCircle, CheckCircle2, Loader2 } from "lucide-react";

interface PlaybackToggleProps {
  enabled: boolean;
  onToggle: (enabled: boolean) => void;
  isConnected: boolean;
  status: "disabled" | "needs-user" | "loading" | "ready" | "error";
  error: string | null;
  isPremium: boolean | undefined;
}

export function PlaybackToggle({
  enabled,
  onToggle,
  isConnected,
  status,
  error,
  isPremium,
}: PlaybackToggleProps) {
  const getStatusMessage = () => {
    if (!isConnected) {
      return {
        icon: <AlertCircle className="w-4 h-4" />,
        message: "Connect Spotify to enable playback",
        variant: "default" as const,
      };
    }
    
    if (error) {
      return {
        icon: <AlertCircle className="w-4 h-4" />,
        message: error,
        variant: "destructive" as const,
      };
    }

    if (isPremium === false) {
      return {
        icon: <AlertCircle className="w-4 h-4" />,
        message: "Spotify Premium is required for in-browser playback",
        variant: "default" as const,
      };
    }

    if (status === "loading") {
      return {
        icon: <Loader2 className="w-4 h-4 animate-spin" />,
        message: "Connecting to the Spotify player...",
        variant: "default" as const,
      };
    }

    if (status === "ready" && enabled) {
      return {
        icon: <CheckCircle2 className="w-4 h-4" />,
        message: "Player ready. Press play on any track below.",
        variant: "default" as const,
      };
    }

    if (status === "error") {
      return {
        icon: <AlertCircle className="w-4 h-4" />,
        message: "Playback unavailable at the moment. Please try again.",
        variant: "destructive" as const,
      };
    }

    return {
      icon: <Headphones className="w-4 h-4" />,
      message: "Toggle playback to listen without leaving the page",
      variant: "default" as const,
    };
  };

  const statusInfo = getStatusMessage();
  // Allow toggle if connected and not explicitly loading
  // Premium check will happen when playback is enabled via the hook
  // Allow toggling even if there's an error (user can retry)
  // When disabled, status is "disabled", so we should allow toggling
  const canToggle = isConnected && status !== "loading";

  // Hide the toggle entirely if user is not premium - just show a message
  if (isConnected && isPremium === false) {
    return (
      <Alert variant="default" className="border-gray-200">
        <AlertCircle className="w-4 h-4" />
        <AlertDescription className="text-sm">
          Spotify Premium is required for in-browser playback. Upgrade your account to enable this feature.
        </AlertDescription>
      </Alert>
    );
  }

  return (
    <div className="space-y-3">
      <div className="flex items-center justify-between p-4 bg-white rounded-xl border border-gray-100">
        <div className="flex items-center gap-3">
          <div className="p-2 rounded-lg" style={{ backgroundColor: 'rgba(29, 185, 84, 0.1)' }}>
            <Headphones className="w-5 h-5" style={{ color: 'var(--spotify-green)' }} />
          </div>
          <Label htmlFor="playback-toggle" className="cursor-pointer">
            Play recommendations here
          </Label>
        </div>
        <div className="flex-shrink-0">
          <Switch
            id="playback-toggle"
            checked={enabled}
            onCheckedChange={onToggle}
            disabled={!canToggle}
            aria-label="Enable playback"
            className={!canToggle ? "opacity-50 cursor-not-allowed" : "cursor-pointer"}
          />
        </div>
      </div>

      <Alert variant={statusInfo.variant} className="border-gray-200">
        {statusInfo.icon}
        <AlertDescription className="text-sm">
          {statusInfo.message}
        </AlertDescription>
      </Alert>
    </div>
  );
}

