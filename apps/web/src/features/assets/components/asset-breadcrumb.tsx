import { Link } from "@tanstack/react-router"

import {
  Breadcrumb,
  BreadcrumbItem,
  BreadcrumbLink,
  BreadcrumbList,
  BreadcrumbPage,
  BreadcrumbSeparator,
} from "@/components/ui/breadcrumb"

export function AssetBreadcrumb({
  assetId,
  assetTitle,
  current,
  releaseId,
}: {
  assetId?: string
  assetTitle?: string
  current?: string
  releaseId?: string
}) {
  return (
    <Breadcrumb>
      <BreadcrumbList>
        <BreadcrumbItem>
          <BreadcrumbLink asChild>
            <Link to="/assets">Assets</Link>
          </BreadcrumbLink>
        </BreadcrumbItem>
        {assetId ? (
          <>
            <BreadcrumbSeparator />
            <BreadcrumbItem>
              {current ? (
                <BreadcrumbLink asChild>
                  <Link
                    to="/assets/$assetId"
                    params={{ assetId }}
                    search={releaseId ? { release: releaseId } : {}}
                  >
                    {assetTitle ?? "Asset"}
                  </Link>
                </BreadcrumbLink>
              ) : (
                <BreadcrumbPage>{assetTitle ?? "Asset"}</BreadcrumbPage>
              )}
            </BreadcrumbItem>
          </>
        ) : null}
        {current ? (
          <>
            <BreadcrumbSeparator />
            <BreadcrumbItem>
              <BreadcrumbPage>{current}</BreadcrumbPage>
            </BreadcrumbItem>
          </>
        ) : null}
      </BreadcrumbList>
    </Breadcrumb>
  )
}
