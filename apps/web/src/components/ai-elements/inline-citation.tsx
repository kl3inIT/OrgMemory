"use client"

import type { CarouselApi } from "@/components/ui/carousel"
import {
  Carousel,
  CarouselContent,
  CarouselItem,
} from "@/components/ui/carousel"
import { Badge } from "@/components/ui/badge"
import {
  HoverCard,
  HoverCardContent,
  HoverCardTrigger,
} from "@/components/ui/hover-card"
import { cn } from "@/lib/utils"
import { ArrowLeftIcon, ArrowRightIcon } from "lucide-react"
import type { ComponentProps, ReactNode } from "react"
import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useState,
} from "react"

export type InlineCitationProps = ComponentProps<"span">

export const InlineCitation = ({ className, ...props }: InlineCitationProps) => (
  <span className={cn("group inline items-center gap-1", className)} {...props} />
)

export type InlineCitationTextProps = ComponentProps<"span">

export const InlineCitationText = ({
  className,
  ...props
}: InlineCitationTextProps) => (
  <span
    className={cn("transition-colors group-hover:bg-accent", className)}
    {...props}
  />
)

export type InlineCitationCardProps = ComponentProps<typeof HoverCard>

export const InlineCitationCard = (props: InlineCitationCardProps) => (
  <HoverCard closeDelay={0} openDelay={0} {...props} />
)

export type InlineCitationCardTriggerProps = ComponentProps<typeof Badge> & {
  sources: string[]
  label?: ReactNode
}

export const InlineCitationCardTrigger = ({
  sources,
  label,
  className,
  ...props
}: InlineCitationCardTriggerProps) => (
  <HoverCardTrigger asChild>
    <Badge
      className={cn("ml-1 cursor-pointer rounded-full", className)}
      variant="secondary"
      {...props}
    >
      {label ?? sourceLabel(sources[0], sources.length)}
    </Badge>
  </HoverCardTrigger>
)

export type InlineCitationCardBodyProps = ComponentProps<"div">

export const InlineCitationCardBody = ({
  className,
  ...props
}: InlineCitationCardBodyProps) => (
  <HoverCardContent className={cn("relative w-80 p-0", className)} {...props} />
)

const CarouselApiContext = createContext<CarouselApi | undefined>(undefined)

const useCarouselApi = () => useContext(CarouselApiContext)

export type InlineCitationCarouselProps = ComponentProps<typeof Carousel>

export const InlineCitationCarousel = ({
  className,
  children,
  ...props
}: InlineCitationCarouselProps) => {
  const [api, setApi] = useState<CarouselApi>()

  return (
    <CarouselApiContext.Provider value={api}>
      <Carousel className={cn("w-full", className)} setApi={setApi} {...props}>
        {children}
      </Carousel>
    </CarouselApiContext.Provider>
  )
}

export type InlineCitationCarouselContentProps = ComponentProps<"div">

export const InlineCitationCarouselContent = (
  props: InlineCitationCarouselContentProps,
) => <CarouselContent {...props} />

export type InlineCitationCarouselItemProps = ComponentProps<"div">

export const InlineCitationCarouselItem = ({
  className,
  ...props
}: InlineCitationCarouselItemProps) => (
  <CarouselItem className={cn("w-full space-y-2 p-4 pl-8", className)} {...props} />
)

export type InlineCitationCarouselHeaderProps = ComponentProps<"div">

export const InlineCitationCarouselHeader = ({
  className,
  ...props
}: InlineCitationCarouselHeaderProps) => (
  <div
    className={cn(
      "flex items-center justify-between gap-2 rounded-t-md bg-secondary p-2",
      className,
    )}
    {...props}
  />
)

export type InlineCitationCarouselIndexProps = ComponentProps<"div">

export const InlineCitationCarouselIndex = ({
  children,
  className,
  ...props
}: InlineCitationCarouselIndexProps) => {
  const api = useCarouselApi()
  const [current, setCurrent] = useState(0)
  const [count, setCount] = useState(0)

  const syncState = useCallback(() => {
    if (!api) return
    setCount(api.scrollSnapList().length)
    setCurrent(api.selectedScrollSnap() + 1)
  }, [api])

  useEffect(() => {
    if (!api) return
    syncState()
    api.on("select", syncState)
    return () => {
      api.off("select", syncState)
    }
  }, [api, syncState])

  return (
    <div
      className={cn(
        "flex flex-1 items-center justify-end px-3 py-1 text-xs text-muted-foreground",
        className,
      )}
      {...props}
    >
      {children ?? `${current}/${count}`}
    </div>
  )
}

export type InlineCitationCarouselPrevProps = ComponentProps<"button">

export const InlineCitationCarouselPrev = ({
  className,
  ...props
}: InlineCitationCarouselPrevProps) => {
  const api = useCarouselApi()
  const handleClick = useCallback(() => api?.scrollPrev(), [api])

  return (
    <button
      aria-label="Previous"
      className={cn("shrink-0", className)}
      onClick={handleClick}
      type="button"
      {...props}
    >
      <ArrowLeftIcon className="size-4 text-muted-foreground" />
    </button>
  )
}

export type InlineCitationCarouselNextProps = ComponentProps<"button">

export const InlineCitationCarouselNext = ({
  className,
  ...props
}: InlineCitationCarouselNextProps) => {
  const api = useCarouselApi()
  const handleClick = useCallback(() => api?.scrollNext(), [api])

  return (
    <button
      aria-label="Next"
      className={cn("shrink-0", className)}
      onClick={handleClick}
      type="button"
      {...props}
    >
      <ArrowRightIcon className="size-4 text-muted-foreground" />
    </button>
  )
}

export type InlineCitationSourceProps = ComponentProps<"div"> & {
  title?: string
  url?: string
  description?: string
}

export const InlineCitationSource = ({
  title,
  url,
  description,
  className,
  children,
  ...props
}: InlineCitationSourceProps) => (
  <div className={cn("space-y-1", className)} {...props}>
    {title ? <h4 className="truncate text-sm font-medium leading-tight">{title}</h4> : null}
    {url ? <p className="truncate break-all text-xs text-muted-foreground">{url}</p> : null}
    {description ? (
      <p className="line-clamp-3 text-sm leading-relaxed text-muted-foreground">
        {description}
      </p>
    ) : null}
    {children}
  </div>
)

export type InlineCitationQuoteProps = ComponentProps<"blockquote">

export const InlineCitationQuote = ({
  children,
  className,
  ...props
}: InlineCitationQuoteProps) => (
  <blockquote
    className={cn(
      "border-l-2 border-muted pl-3 text-sm italic text-muted-foreground",
      className,
    )}
    {...props}
  >
    {children}
  </blockquote>
)

function sourceLabel(source: string | undefined, count: number) {
  if (!source) return "unknown"
  try {
    const url = new URL(source, "https://orgmemory.local")
    const hostname = url.hostname === "orgmemory.local" ? "OrgMemory" : url.hostname
    return count > 1 ? `${hostname} +${count - 1}` : hostname
  } catch {
    return "unknown"
  }
}
