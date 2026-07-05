import { Moon, Sun } from "lucide-react"
import { useTheme } from "next-themes"
import { SidebarMenuButton } from "@/components/ui/sidebar"

export function ModeToggle() {
  const { theme, setTheme } = useTheme()
  const isDark = theme === "dark"

  return (
    <SidebarMenuButton tooltip="Theme" onClick={() => setTheme(isDark ? "light" : "dark")}>
      {isDark ? <Moon /> : <Sun />}
      <span>{isDark ? "Dark mode" : "Light mode"}</span>
    </SidebarMenuButton>
  )
}
