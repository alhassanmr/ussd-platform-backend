-- V11__menu_linking.sql
-- Allow menu items to link to another menu (for navigation flow)
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS next_menu_id UUID REFERENCES menus(id) ON DELETE SET NULL;
ALTER TABLE menu_items ADD COLUMN IF NOT EXISTS display_order_seq INT DEFAULT 0;
CREATE INDEX IF NOT EXISTS idx_menu_items_next ON menu_items(next_menu_id);
