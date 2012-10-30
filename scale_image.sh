original=$1
convert -resize 18x18 res/drawable/$original res/drawable-ldpi/$original
convert -resize 24x24 res/drawable/$original res/drawable-mdpi/$original
convert -resize 36x36 res/drawable/$original res/drawable-hdpi/$original
convert -resize 48x48 res/drawable/$original res/drawable-xhdpi/$original
