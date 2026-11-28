interface PaginationProps {
  page: number;
  size: number;
  total: number;
  onPageChange: (page: number) => void;
  ariaLabel: string;
}

// 页码窗口大小：当前页前后各 2 页，加上首尾页共 5 个连续页码
const WINDOW = 5;
const GAP = -1;

function buildPageItems(currentPage: number, totalPages: number): number[] {
  if (totalPages <= WINDOW + 2) {
    return Array.from({ length: totalPages }, (_, index) => index);
  }

  const items: number[] = [];
  const start = Math.max(1, Math.min(currentPage - 2, totalPages - WINDOW - 1));
  const end = Math.min(totalPages - 2, start + WINDOW - 1);

  items.push(0);
  if (start > 1) items.push(GAP);
  for (let pageIndex = start; pageIndex <= end; pageIndex += 1) {
    items.push(pageIndex);
  }
  if (end < totalPages - 2) items.push(GAP);
  items.push(totalPages - 1);

  return items;
}

export function Pagination({ page, size, total, onPageChange, ariaLabel }: PaginationProps) {
  const totalPages = Math.max(1, Math.ceil(total / Math.max(1, size)));
  const currentPage = Math.min(Math.max(page, 0), totalPages - 1);
  const pageItems = buildPageItems(currentPage, totalPages);

  return (
    <nav className="pagination" aria-label={ariaLabel}>
      <button
        type="button"
        className="button button-pager"
        onClick={() => onPageChange(currentPage - 1)}
        disabled={currentPage === 0}
      >
        上一页
      </button>
      <div className="pagination-pages">
        {pageItems.map((item, index) =>
          item === GAP ? (
            <span key={`gap-${index}`} className="pagination-ellipsis" aria-hidden="true">
              …
            </span>
          ) : (
            <button
              key={item}
              type="button"
              className={`button button-pager ${item === currentPage ? "pager-active" : ""}`}
              aria-current={item === currentPage ? "page" : undefined}
              aria-label={`第 ${item + 1} 页`}
              onClick={() => onPageChange(item)}
            >
              {item + 1}
            </button>
          ),
        )}
      </div>
      <button
        type="button"
        className="button button-pager"
        onClick={() => onPageChange(currentPage + 1)}
        disabled={currentPage >= totalPages - 1}
      >
        下一页
      </button>
    </nav>
  );
}
