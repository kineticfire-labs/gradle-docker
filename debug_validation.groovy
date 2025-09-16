// Quick debug script to test our validation logic
def imageRef = 'registry.example.com/myapp:1.0.0'

// Find all colons
def colons = []
for (int i = 0; i < imageRef.length(); i++) {
    if (imageRef.charAt(i) == ':') {
        colons.add(i)
    }
}

println "Image ref: $imageRef"
println "Colons at positions: $colons"

// The tag separator is the last colon, unless it's preceded by a slash
int tagSeparator = -1
for (int i = colons.size() - 1; i >= 0; i--) {
    int colonPos = colons[i]
    println "Checking colon at position $colonPos"
    
    // Check if this colon is preceded by a slash (indicating it's a port)
    boolean isPort = false
    for (int j = colonPos - 1; j >= 0; j--) {
        char c = imageRef.charAt(j)
        println "  Char at $j: '$c'"
        if (c == '/') {
            isPort = true
            println "  Found slash, this is a port"
            break
        } else if (c == ':') {
            println "  Found another colon, this isn't a port"
            break
        }
    }
    
    if (!isPort) {
        tagSeparator = colonPos
        println "Tag separator found at: $tagSeparator"
        break
    }
}

def imageName = imageRef.substring(0, tagSeparator)
def tag = imageRef.substring(tagSeparator + 1)

println "Image name: '$imageName'"
println "Tag: '$tag'"