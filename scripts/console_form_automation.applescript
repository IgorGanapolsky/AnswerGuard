-- AppleScript to automate Google Play Console "App Content" declarations
-- Focuses the browser and clicks through common forms for a private utility app.

on run argv
	set browserName to "Google Chrome"
	if count of argv is greater than 0 then
		set browserName to item 1 of argv
	end if

	tell application browserName
		activate
		-- Assume the user has the App Content page open.
		-- We use JavaScript execution to click specific buttons reliably.

		tell window 1 to tell active tab
			-- 1. ADVERTISING ID
			execute javascript "
				let startBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('Start') && b.closest('[aria-label*=\"Advertising ID\"]'));
				if (startBtn) startBtn.click();
			"
			delay 2
			execute javascript "
				let noRadio = document.querySelector('input[type=\"radio\"][value=\"false\"]') || Array.from(document.querySelectorAll('label')).find(l => l.innerText.includes('No')).previousElementSibling;
				if (noRadio) noRadio.click();
				let saveBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('Save') || b.innerText.includes('Submit'));
				if (saveBtn) saveBtn.click();
			"
			delay 2

			-- 2. TARGET AUDIENCE
			execute javascript "
				let startBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('Start') && b.closest('[aria-label*=\"Target audience\"]'));
				if (startBtn) startBtn.click();
			"
			delay 2
			execute javascript "
				let eighteenPlus = Array.from(document.querySelectorAll('label')).find(l => l.innerText.includes('18 and over'));
				if (eighteenPlus) eighteenPlus.click();
				let nextBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('Next'));
				if (nextBtn) nextBtn.click();
				// Confirming no appeal to children
				let noRadio = document.querySelector('input[type=\"radio\"][value=\"false\"]');
				if (noRadio) noRadio.click();
				nextBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('Next'));
				if (nextBtn) nextBtn.click();
				let saveBtn = Array.from(document.querySelectorAll('button')).find(b => b.innerText.includes('Save'));
				if (saveBtn) saveBtn.click();
			"
		end tell
	end tell
end run
