import { Button } from "./ui/button";
import { Input } from "./ui/input";
import { Label } from "./ui/label";
import { Sparkles, Loader2 } from "lucide-react";

interface RecommendationControlsProps {
  limit: number;
  onLimitChange: (limit: number) => void;
  onFetchRecommendations: () => void;
  isLoading: boolean;
  disabled: boolean;
}

export function RecommendationControls({
  limit,
  onLimitChange,
  onFetchRecommendations,
  isLoading,
  disabled,
}: RecommendationControlsProps) {
  return (
    <div className="space-y-3">
      <Label htmlFor="limit-input" className="text-gray-900">
        Number of recommendations
      </Label>
      <div className="flex gap-3">
        <Input
          id="limit-input"
          type="number"
          min={5}
          max={50}
          value={limit}
          onChange={(e) => onLimitChange(Number(e.target.value))}
          className="h-12 border-gray-200"
          style={{
            '--tw-ring-color': 'var(--spotify-green)',
          } as React.CSSProperties}
        />
        <Button
          onClick={onFetchRecommendations}
          disabled={disabled || isLoading}
          className="gap-2 h-12 px-6 text-white hover:opacity-90"
          style={{ background: !disabled && !isLoading ? 'var(--spotify-green)' : undefined }}
        >
          {isLoading ? (
            <>
              <Loader2 className="w-4 h-4 animate-spin" />
              Fetching...
            </>
          ) : (
            <>
              <Sparkles className="w-4 h-4" />
              Get recommendations
            </>
          )}
        </Button>
      </div>
    </div>
  );
}


