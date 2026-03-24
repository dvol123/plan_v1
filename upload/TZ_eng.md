Technical Specification for the Mobile Application "Plan"

1\. General Description

The application allows the user to upload a photo, mark interactive
areas (buttons) on it using a coordinate grid, and attach text comments,
photos, and videos to each area. The application runs on Android 14 and
higher, is developed in Android Studio using Kotlin, and the source code
is hosted on GitHub.

2\. Project Management

A project is a unit of content that includes:

the main photo,

a set of areas (buttons),

media files and texts attached to the areas.

Project fields:

name (required),

type 1 (text, optional),

type 2 (text, optional),

description (text, optional),

note (text, optional).

Creating a project:

The user selects a photo from the gallery or takes a photo with the
camera.

After obtaining the photo, a form opens where the name is mandatory,
other fields are optional.

After saving, the project appears in the list on the main screen, and
the application switches to the project screen in editing mode (grid).

Editing a project (only fields: type 1, type 2, description, note):

Available on the main screen via the "Pencil" button when a project is
selected.

The name and photo cannot be edited.

Deleting a project:

Via the menu on the project screen (item "Delete") with confirmation.
After deletion, the application returns to the main screen.

It is also possible to delete a project via the edit card on the main
screen.

3\. Main Screen (Screen 1)

3.1. Header

Search bar for project names (filters the list in real time).

Menu button (four horizontal stripes) on the right. The menu contains:

Add / Добавить / 添加 --- create a new project (select photo, then fill
in fields).

Import / Импорт / 导入 --- import a project from a ZIP archive.

Share / Обмен / 分享 --- receive and send a project via Wi‑Fi/Bluetooth.

Export / Экспорт / 导出 --- export for PC (create HTML + media folders).
If a project is selected, it is exported; otherwise a dialog opens to
choose: export all projects or select one from the list.

Settings / Настройки / 设置 --- language selection (Russian, English,
Chinese) and theme (system, light, dark).

Exit / Выход / 退出 --- close the application with confirmation if there
are unsaved changes in an open project.

3.2. Body

List of projects. Each item: project name on the left, photo thumbnail
on the right.

Double‑tap on the name selects the project (highlighted). When a project
is selected, the bottom bar buttons (Star, Pencil, Checkmark) become
active.

Single‑tap on the thumbnail opens the photo in full‑screen view (without
markup).

3.3. Bottom Bar

Always visible, respects the system navigation bar insets. Four buttons:

Home / Домик / 首页 --- main screen (deselects any selected project).

Star / Звезда / 星标 --- active only when a project is selected. Opens
the project screen in view mode.

Pencil / Карандаш / 编辑 --- active only when a project is selected.
Opens a dialog to edit project fields (type 1, type 2, description,
note). This dialog also contains a "Delete project" button. After saving
or deletion, the application returns to the main screen and the
selection is cleared.

Checkmark / Галочка / 查看 --- active only when a project is selected.
Opens a dialog to view all project fields (read‑only).

4\. Project Screen (Screen 2)

4.1. Header

Name of the current project.

Search bar --- searches area (button) names. As text is entered, areas
whose names contain the search string are highlighted on the photo
(e.g., with a bright border).

Menu button (three dots or four stripes) on the right. The menu
contains:

Edit / Редактировать / 编辑 --- switch to editing mode (grid) and exit
it.

Export / Экспорт / 导出 --- export the current project for PC (HTML +
folders).

Share / Поделиться / 分享 --- send the current project to another device
via Wi‑Fi/Bluetooth (ZIP archive).

Delete / Удалить / 删除 --- delete the current project with
confirmation; after deletion, go to the main screen.

Clear / Очистить / 清除 --- delete all areas of the current project with
confirmation (areas and their content are deleted, the project remains).

4.2. Body

Photo with zoom (two‑finger pinch) and panning support.

On top of the photo, either areas (buttons) in view mode or the grid in
editing mode are displayed.

4.3. Bottom Bar

Home / Домик / 首页 --- return to the main screen. If there are unsaved
changes (in editing mode or in an open card with changes), a
confirmation dialog is shown.

Star / Звезда / 星标 --- not used on screen 2 (inactive).

Pencil / Карандаш / 编辑 --- active when an area card is open. Pressing
it switches the card to editing mode.

Checkmark / Галочка / 保存 --- active when an area card is open and
changes have been made. Pressing it saves the changes (with confirmation
dialog).

4.4. View Mode

Areas are displayed with a semi‑transparent fill of the chosen color
(50% opacity).

Single‑tap on an area does nothing.

Double‑tap on an area opens the area card.

4.5. Area Card

Opens as a scrollable BottomSheetDialogFragment. Contains:

Media gallery (carousel) of attached photos and videos. Tapping an
element opens full‑screen view: for photos -- zoom and swipe between
media; for videos -- playback.

Fields: name (required), type 1, type 2, state (choose from existing or
create new), description, note.

Buttons Add photo / Добавить фото / 添加照片 and Add video / Добавить
видео / 添加视频 --- open selection from gallery/camera.

View mode of the card:

All fields are read‑only.

Bottom bar buttons Pencil and Checkmark are active.

Edit mode of the card:

Opened by pressing Pencil.

Fields become editable, a "Save" button appears (or Checkmark in the
bottom bar).

When saved, data is updated in the database; the area's color on the
photo changes according to the chosen state.

When closing the card with unsaved changes, a dialog is shown with
options: save, discard, cancel.

State:

Choose from previously created states (name + color) or create a new
one.

Colors of states --- 7 classic colors: red, orange, yellow, green, cyan,
blue, violet.

When creating a new state, the user enters a name and selects one of the
7 colors.

4.6. Editing Mode (Grid)

Enter and exit via the menu item Edit.

A square coordinate grid is displayed on top of the photo. Existing
areas are hidden.

Grid cell size is adjusted with a slider and "+" / "-" buttons located
above the bottom bar. Cell size can only be changed if the project has
no areas yet. If areas exist, a warning appears that changing the size
will delete all areas; the user must confirm or cancel.

Available cell sizes are multiples of 2 relative to the original photo
size (1, 2, 4, 8, etc.).

Creating an area:

Double‑tap on a cell starts a new selection (previous selection is
cleared).

Single‑tap adds a cell to the selection only if it is adjacent (by side
or diagonal) to already selected cells.

The last added cell can be removed by tapping it again.

After finishing the selection, press Create (or Checkmark). A dialog
opens:

Area name (required).

Choose a state (existing or new).

Fields: type 1, type 2, description, note (optional).

After saving, the area is created and the selection is cleared.

Deleting all areas --- via the menu item Clear.

5\. Database

Storage -- Room. Entities:

projects: id (Long), name (String), photoUri (String), type1 (String?),
type2 (String?), description (String?), note (String?), cellSize (Int),
createdAt (Long), updatedAt (Long).

states: id (Long), name (String), color (Int). Pre‑filled with 7 colors.

regions: id (Long), projectId (Long), name (String), stateId (Long?),
type1 (String?), type2 (String?), description (String?), note (String?),
cellsJson (String), createdAt (Long), updatedAt (Long). cellsJson stores
an array of cells as \[{\"row\":0,\"col\":0}, \...\].

contents: id (Long), regionId (Long), type (String ---
\"text\"/\"photo\"/\"video\"), data (String), sortOrder (Int), createdAt
(Long). For text, data contains the text; for photo/video, the relative
path to the file.

6\. Import and Export of Projects

6.1. Export for Transfer to Another Device (ZIP)

A ZIP archive is created with the following structure:

project.json (project fields except id and photoUri).

photo.jpg (original photo).

folder regions/ with JSON files for each area, including state
information (name and color) and the list of contents (with relative
paths to media).

folder media/ with all attached photos and videos.

6.2. Import from ZIP

The application extracts the archive, creates a new project, restores
states (by name and color -- either links to existing ones or creates
new ones), copies media files to private storage. After import, the
project appears in the list.

6.3. Export for PC (HTML + Folders)

A folder with the project name is created, containing:

photo_with_areas.jpg --- original photo with overlaid areas (borders and
semi‑transparent fill using the state color).

Subfolders for each area, into which attached media files are copied and
a comment.txt (text comment, if any) is created.

report.html --- a table listing all areas, their fields, and links to
the files.

7\. Device‑to‑Device Sharing

Project transfer is performed via Wi‑Fi Direct or Bluetooth. A ZIP
archive prepared according to the transfer export format is sent. The
receiving device automatically recognizes the archive, runs the import,
and adds the project to the list.

8\. Non‑Functional Requirements

System navigation bar adaptation: the bottom bar receives additional
padding to avoid being overlapped by the navigation buttons.

Keyboard: all screens with text input use adjustResize so that input
fields are not covered by the keyboard.

Confirmations: any action that may result in data loss (deleting a
project, clearing areas, exiting without saving) shows a dialog with
options: Save, Don't Save, Cancel (where applicable).

Localization: all interface texts are externalized into resources for
three languages: Russian, English, Chinese.

Free libraries: all third‑party libraries used must have open licenses
(Apache 2.0, MIT, etc.).

9\. Architecture and Technology Stack

Architecture: MVVM with Clean Architecture (layers: data, domain,
presentation). Use of repositories and use cases.

Managers (singletons): ProjectManager, GridManager, ExportManager,
TransferManager -- for centralized management of state and complex
operations.

Language: Kotlin.

UI: Jetpack Compose (XML with custom Views is also allowed, at the
developer's choice).

Database: Room.

Photo zoom: PhotoView.

Camera: CameraX.

Image loading: Glide or Coil.

Wi‑Fi Direct / Bluetooth: built‑in Android APIs.

ZIP: built‑in ZipOutputStream / ZipInputStream.

Coroutines: Kotlin Coroutines.

10\. Development Stages

Project setup, adding dependencies, creating directory structure.

Implementing the database (entities, DAOs, repositories, pre‑populating
states).

Developing the main screen (project list, creation, editing, deletion,
search).

Implementing the project screen (basic functionality without the grid):
photo display, loading areas, drawing fills, area search, area card
(view, edit fields, add media, carousel, full‑screen view).

Implementing the editing mode (grid): displaying the grid, changing cell
size, selecting adjacent cells, creating an area, deleting all areas.

Import/export of ZIP (for device transfer) and export for PC (HTML +
folders).

Implementing Wi‑Fi Direct / Bluetooth sharing.

Localization, settings (language, theme).

Testing and debugging.
